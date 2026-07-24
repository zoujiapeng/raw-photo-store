from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4

from fastapi import Depends, FastAPI, File, Form, HTTPException, Query, UploadFile, status
from fastapi.responses import FileResponse
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import and_, or_, select
from sqlalchemy.orm import Session

from .config import settings
from .database import Base, SessionLocal, engine, get_db
from .models import Appeal, AuditEvent, Favorite, LicenseGrant, Report, Review, VoteEvent, Work
from .schemas import AppealCreate, AuditOut, BlindPairOut, BlindVoteRequest, FavoriteRequest, HealthOut, LicenseOut, LicensePurchase, MessageOut, ReportCreate, ReviewCreate, ReviewOut, WorkOut
from .seed import seed_official_samples
from .serializers import audit_out, license_out, review_out, work_out
from .services.ai_provider import ai_provider
from .services.moderation import moderate
from .services.review_quality import analyze_review
from .services.scoring import apply_blind_vote, apply_review, place_work, reviewer_trust
from .services.storage import save_upload

@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(bind=engine)
    with SessionLocal() as db:
        seed_official_samples(db)
    yield

app = FastAPI(title="RAWJudge API", version="0.1.0", description="RAW-certified photography review, blind scoring, comment quality and audit API.", lifespan=lifespan)
origins = [item.strip() for item in settings.cors_origins.split(",") if item.strip()]
app.add_middleware(CORSMiddleware, allow_origins=origins or ["*"], allow_credentials=False, allow_methods=["*"], allow_headers=["*"])

@app.get("/health", response_model=HealthOut)
def health() -> HealthOut:
    return HealthOut(status="ok", database="sqlite" if settings.database_url.startswith("sqlite") else "external", ai_mode="openai" if ai_provider.enabled else "deterministic-local")

@app.get("/v1/works", response_model=list[WorkOut])
def list_works(partition: str | None = Query(default=None, pattern="^(gallery|review|workshop|archive)$"), db: Session = Depends(get_db)) -> list[WorkOut]:
    statement = select(Work).order_by(Work.created_at.desc())
    if partition:
        statement = statement.where(Work.partition == partition)
    return [work_out(work) for work in db.scalars(statement).unique().all()]

@app.get("/v1/works/{work_id}", response_model=WorkOut)
def get_work(work_id: int, db: Session = Depends(get_db)) -> WorkOut:
    return work_out(_get_work(db, work_id))

@app.post("/v1/works", response_model=WorkOut, status_code=status.HTTP_201_CREATED)
async def create_work(title: str = Form(..., min_length=1, max_length=180), description: str = Form(default="", max_length=5000), author_name: str = Form(default="匿名摄影者", max_length=120), handle: str = Form(default="@anonymous", max_length=120), external_url: str | None = Form(default=None), allow_preview: bool = Form(default=True), allow_raw: bool = Form(default=False), preview_price: float = Form(default=0.8, ge=0.1, le=1000), raw_price: float = Form(default=1.8, ge=0.1, le=10000), image: UploadFile = File(...), raw: UploadFile | None = File(default=None), db: Session = Depends(get_db)) -> WorkOut:
    token = uuid4().hex
    image_file = await save_upload(image, settings.upload_dir / "images", settings.max_image_bytes, f"{token}-image")
    raw_file = await save_upload(raw, settings.upload_dir / "raw", settings.max_raw_bytes, f"{token}-raw") if raw else None
    result = await moderate(title=title, description=description, image_name=image_file.original_name, raw_name=raw_file.original_name if raw_file else None, image_hash=image_file.sha256, raw_hash=raw_file.sha256 if raw_file else None)
    work = Work(title=title, description=description, author_name=author_name, handle=handle, external_url=external_url, image_path=image_file.path, image_name=image_file.original_name, image_sha256=image_file.sha256, raw_path=raw_file.path if raw_file else None, raw_name=raw_file.original_name if raw_file else None, raw_sha256=raw_file.sha256 if raw_file else None, raw_verified=result.raw_verified, score=result.score, confidence=result.confidence, partition="review", moderation_status=result.status, moderation_summary=result.summary, risk_labels=",".join(result.labels))
    work.partition = place_work(work)
    work.audit_events.extend([AuditEvent(kind="upload", message=f"成片已保存；SHA-256 {image_file.sha256[:12]}…", actor_id=handle), AuditEvent(kind="raw", message=(f"RAW 已保存；SHA-256 {raw_file.sha256[:12]}…" if raw_file else "未上传 RAW。"), actor_id=handle), AuditEvent(kind="moderation", message=result.summary, actor_id="ai-agent")])
    ai_body = "系统已完成文件规则检查。下一步由盲评和具体评论更新质量分；AI 初评不会直接决定最终分区。"
    metrics = analyze_review(ai_body)
    work.reviews.append(Review(reviewer_id="ai-agent", author_name="AI 初评（已标注）", body=ai_body, score=round(result.score), relevance=metrics.relevance, professional=metrics.professional, technical=metrics.technical, objective=metrics.objective, constructive=metrics.constructive, reviewer_trust=0.35, is_ai=True, folded=False, score_counted=False))
    if allow_preview:
        work.licenses.append(LicenseGrant(license_type="preview", price=preview_price, terms="个人欣赏与收藏；禁止商用、转售和 AI 训练。"))
    if allow_raw and result.raw_verified:
        work.licenses.append(LicenseGrant(license_type="raw_study", price=raw_price, terms="仅学习研究；版权仍归作者；禁止二次传播。"))
    db.add(work); db.commit(); db.refresh(work)
    return work_out(work)

@app.post("/v1/works/{work_id}/favorite", response_model=WorkOut)
def favorite_work(work_id: int, request: FavoriteRequest, db: Session = Depends(get_db)) -> WorkOut:
    work = _get_work(db, work_id)
    existing = db.scalar(select(Favorite).where(Favorite.work_id == work.id, Favorite.user_id == request.user_id))
    changed = False
    if request.favorite and existing is None:
        db.add(Favorite(work_id=work.id, user_id=request.user_id)); work.favorites += 1; changed = True
    elif not request.favorite and existing is not None:
        db.delete(existing); work.favorites = max(0, work.favorites - 1); changed = True
    work.audit_events.append(AuditEvent(kind="favorite", message=("收藏状态改变；社交数据不进入质量分。" if changed else "重复收藏请求被按幂等操作忽略。"), actor_id=request.user_id))
    db.commit(); return work_out(work)

@app.post("/v1/works/{work_id}/reviews", response_model=ReviewOut, status_code=status.HTTP_201_CREATED)
def create_review(work_id: int, request: ReviewCreate, db: Session = Depends(get_db)) -> ReviewOut:
    work = _get_work(db, work_id)
    metrics = analyze_review(request.body); trust = reviewer_trust(db, request.reviewer_id, work.id)
    quality = max(0, min(1, metrics.average * 0.72 + trust * 0.28)); extreme = request.score <= 10 or request.score >= 95
    score_counted = quality >= 0.32 and (not extreme or (len(request.body.strip()) >= 40 and metrics.average >= 0.45))
    review = Review(work_id=work.id, reviewer_id=request.reviewer_id, author_name=request.author_name, body=request.body.strip(), score=request.score, relevance=metrics.relevance, professional=metrics.professional, technical=metrics.technical, objective=metrics.objective, constructive=metrics.constructive, reviewer_trust=trust, folded=len(request.body.strip()) < 8 or quality < 0.32, score_counted=score_counted)
    db.add(review)
    if score_counted: apply_review(work, request.score, metrics, trust)
    work.audit_events.append(AuditEvent(kind="review", message=f"评语主要类型 {metrics.strongest}；质量权重 {quality:.2f}；" + ("评分已计入。" if score_counted else "评分未计入。"), actor_id=request.reviewer_id))
    db.commit(); db.refresh(review); return review_out(review)

@app.get("/v1/blind/pair", response_model=BlindPairOut)
def blind_pair(sequence: int = Query(default=0, ge=0), db: Session = Depends(get_db)) -> BlindPairOut:
    candidates = db.scalars(select(Work).where(Work.raw_verified.is_(True), Work.moderation_status.not_in(("rejected", "needs_raw"))).order_by(Work.id)).unique().all()
    if len(candidates) < 2: raise HTTPException(status_code=409, detail="Not enough eligible works")
    return BlindPairOut(left=work_out(candidates[sequence % len(candidates)]), right=work_out(candidates[(sequence + 1) % len(candidates)]), sequence=sequence)

@app.post("/v1/blind/vote", response_model=MessageOut)
def blind_vote(request: BlindVoteRequest, db: Session = Depends(get_db)) -> MessageOut:
    if request.left_work_id == request.right_work_id: raise HTTPException(status_code=400, detail="Pair must contain two works")
    left = _get_work(db, request.left_work_id); right = _get_work(db, request.right_work_id)
    recent_cutoff = datetime.now(timezone.utc) - timedelta(hours=24)
    duplicate = db.scalar(select(VoteEvent).where(VoteEvent.voter_id == request.voter_id, VoteEvent.created_at >= recent_cutoff, or_(and_(VoteEvent.left_work_id == left.id, VoteEvent.right_work_id == right.id), and_(VoteEvent.left_work_id == right.id, VoteEvent.right_work_id == left.id))))
    weight = 0.0 if duplicate else reviewer_trust(db, request.voter_id)
    if request.winner_work_id not in {None, left.id, right.id}: raise HTTPException(status_code=400, detail="Winner is outside pair")
    if request.winner_work_id is not None and weight > 0:
        apply_blind_vote(left, request.winner_work_id == left.id, weight); apply_blind_vote(right, request.winner_work_id == right.id, weight)
    db.add(VoteEvent(voter_id=request.voter_id, left_work_id=left.id, right_work_id=right.id, winner_work_id=request.winner_work_id, weight=weight)); db.commit()
    return MessageOut(message=f"Blind vote recorded with weight {weight:.2f}")

@app.post("/v1/works/{work_id}/appeals", response_model=MessageOut, status_code=201)
def appeal(work_id: int, request: AppealCreate, db: Session = Depends(get_db)) -> MessageOut:
    work = _get_work(db, work_id); db.add(Appeal(work_id=work.id, author_id=request.author_id, reason=request.reason)); work.moderation_status="appealing"; work.partition="archive"; db.commit(); return MessageOut(message="Appeal queued")

@app.post("/v1/works/{work_id}/reports", response_model=MessageOut, status_code=201)
def report(work_id: int, request: ReportCreate, db: Session = Depends(get_db)) -> MessageOut:
    _get_work(db, work_id); db.add(Report(work_id=work_id, reporter_id=request.reporter_id, reason=request.reason)); db.commit(); return MessageOut(message="Report accepted")

@app.post("/v1/works/{work_id}/licenses/{license_type}", response_model=LicenseOut, status_code=201)
def purchase_license(work_id: int, license_type: str, request: LicensePurchase, db: Session = Depends(get_db)) -> LicenseOut:
    work = _get_work(db, work_id)
    license_row = next((item for item in work.licenses if item.license_type == license_type), None)
    if license_row is None: raise HTTPException(status_code=404, detail="License unavailable")
    existing = db.scalar(select(LicenseGrant).where(LicenseGrant.work_id == work.id, LicenseGrant.license_type == license_type, LicenseGrant.buyer_id == request.buyer_id, LicenseGrant.granted.is_(True)))
    if existing: raise HTTPException(status_code=409, detail="License already granted")
    grant = LicenseGrant(work_id=work.id, license_type=license_type, price=license_row.price, terms=license_row.terms, buyer_id=request.buyer_id, granted=True, granted_at=datetime.now(timezone.utc)); db.add(grant); work.downloads += 1; db.commit(); db.refresh(grant); return license_out(grant)

@app.get("/v1/works/{work_id}/image")
def image(work_id: int, db: Session = Depends(get_db)) -> FileResponse:
    work = _get_work(db, work_id)
    if not work.image_path or not Path(work.image_path).exists(): raise HTTPException(status_code=404, detail="Image unavailable")
    return FileResponse(work.image_path, filename=work.image_name)

@app.get("/v1/works/{work_id}/raw")
def raw_download(work_id: int, buyer_id: str = Query(min_length=1), db: Session = Depends(get_db)) -> FileResponse:
    work = _get_work(db, work_id)
    if not work.raw_path or not Path(work.raw_path).exists(): raise HTTPException(status_code=404, detail="RAW unavailable")
    grant = db.scalar(select(LicenseGrant).where(LicenseGrant.work_id == work.id, LicenseGrant.license_type == "raw_study", LicenseGrant.buyer_id == buyer_id, LicenseGrant.granted.is_(True)))
    if grant is None: raise HTTPException(status_code=403, detail="RAW license required")
    return FileResponse(work.raw_path, filename=work.raw_name)

def _get_work(db: Session, work_id: int) -> Work:
    work = db.get(Work, work_id)
    if work is None: raise HTTPException(status_code=404, detail="Work not found")
    return work
