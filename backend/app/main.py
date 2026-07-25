from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4

from fastapi import Depends, FastAPI, File, Form, HTTPException, Query, UploadFile, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.trustedhost import TrustedHostMiddleware
from fastapi.responses import FileResponse
from sqlalchemy import and_, or_, select, text
from sqlalchemy.orm import Session

from .auth import Principal, issue_token, optional_principal, require_admin, require_user
from .config import settings
from .database import Base, SessionLocal, engine, get_db
from .models import (
    Appeal,
    AuditEvent,
    Favorite,
    LicenseGrant,
    Report,
    Review,
    User,
    VoteEvent,
    Work,
)
from .schemas import (
    AdminModerationUpdate,
    AnonymousAuthCreate,
    AppealCreate,
    AuthOut,
    BlindPairOut,
    BlindVoteRequest,
    FavoriteRequest,
    HealthOut,
    LicenseOut,
    MessageOut,
    ReadyOut,
    ReportCreate,
    ReviewCreate,
    ReviewOut,
    UserOut,
    UserUpdate,
    WorkOut,
)
from .seed import seed_official_samples
from .serializers import blind_work_out, license_out, review_out, user_out, work_out
from .services.ai_provider import ai_provider
from .services.moderation import moderate
from .services.review_quality import analyze_review
from .services.scoring import apply_blind_vote, apply_review, place_work, reviewer_trust
from .services.storage import (
    create_public_preview,
    remove_files,
    save_upload,
    validate_image,
    validate_raw,
)

PUBLIC_STATUSES = ("reviewing", "published")


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(bind=engine)
    if settings.seed_demo_data:
        with SessionLocal() as db:
            seed_official_samples(db)
    yield


app = FastAPI(
    title="RAWJudge API",
    version="0.3.0",
    description="Authenticated RAW-certified photography review API.",
    lifespan=lifespan,
)
origins = [item.strip() for item in settings.cors_origins.split(",") if item.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)
hosts = [item.strip() for item in settings.trusted_hosts.split(",") if item.strip()]
app.add_middleware(TrustedHostMiddleware, allowed_hosts=hosts or ["*"])


@app.get("/health", response_model=HealthOut)
def health() -> HealthOut:
    return HealthOut(
        status="ok",
        database="sqlite" if settings.database_url.startswith("sqlite") else "external",
        ai_mode="openai" if ai_provider.enabled else "deterministic-local",
    )


@app.get("/ready", response_model=ReadyOut)
def ready(db: Session = Depends(get_db)) -> ReadyOut:
    db.execute(text("SELECT 1"))
    return ReadyOut(
        status="ready",
        database="sqlite" if settings.database_url.startswith("sqlite") else "external",
    )


@app.post("/v1/auth/anonymous", response_model=AuthOut, status_code=201)
def anonymous_auth(request: AnonymousAuthCreate, db: Session = Depends(get_db)) -> AuthOut:
    if not settings.allow_registration:
        raise HTTPException(status_code=403, detail="Registration is disabled")
    token, digest = issue_token()
    handle = _available_handle(db, request.handle or request.display_name)
    user = User(
        display_name=request.display_name.strip(),
        handle=handle,
        token_hash=digest,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return AuthOut(access_token=token, user=user_out(user))


@app.get("/v1/me", response_model=UserOut)
def get_me(
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> UserOut:
    return user_out(_get_user(db, principal))


@app.patch("/v1/me", response_model=UserOut)
def update_me(
    request: UserUpdate,
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> UserOut:
    user = _get_user(db, principal)
    if request.display_name is not None:
        user.display_name = request.display_name.strip()
    if request.handle is not None:
        normalized = _normalize_handle(request.handle)
        existing = db.scalar(select(User).where(User.handle == normalized, User.id != user.id))
        if existing:
            raise HTTPException(status_code=409, detail="Handle is already in use")
        user.handle = normalized
    if request.external_url is not None:
        user.external_url = str(request.external_url)
    db.commit()
    db.refresh(user)
    return user_out(user)


@app.get("/v1/works", response_model=list[WorkOut])
def list_works(
    partition: str | None = Query(default=None, pattern="^(gallery|review|workshop|archive)$"),
    limit: int = Query(default=40, ge=1),
    principal: Principal | None = Depends(optional_principal),
    db: Session = Depends(get_db),
) -> list[WorkOut]:
    limit = min(limit, settings.max_page_size)
    statement = select(Work).order_by(Work.created_at.desc())
    if not (principal and principal.is_admin):
        public = or_(
            Work.official_sample.is_(True),
            and_(Work.raw_verified.is_(True), Work.moderation_status.in_(PUBLIC_STATUSES)),
        )
        if principal and principal.user_id is not None:
            statement = statement.where(or_(public, Work.owner_id == principal.user_id))
        else:
            statement = statement.where(public)
    if partition:
        statement = statement.where(Work.partition == partition)
    works = db.scalars(statement.limit(limit)).unique().all()
    return [work_out(work, principal, db) for work in works]


@app.get("/v1/works/{work_id}", response_model=WorkOut)
def get_work(
    work_id: int,
    principal: Principal | None = Depends(optional_principal),
    db: Session = Depends(get_db),
) -> WorkOut:
    return work_out(_get_visible_work(db, work_id, principal), principal, db)


@app.post("/v1/works", response_model=WorkOut, status_code=status.HTTP_201_CREATED)
async def create_work(
    title: str = Form(..., min_length=1, max_length=180),
    description: str = Form(default="", max_length=5000),
    allow_preview: bool = Form(default=True),
    allow_raw: bool = Form(default=False),
    preview_price: float = Form(default=0.0, ge=0.0, le=1000),
    raw_price: float = Form(default=0.0, ge=0.0, le=10000),
    image: UploadFile = File(...),
    raw: UploadFile | None = File(default=None),
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> WorkOut:
    user = _get_user(db, principal)
    token = uuid4().hex
    image_file = None
    raw_file = None
    preview_path = None
    try:
        image_file = await save_upload(
            image,
            settings.upload_dir / "originals",
            settings.max_image_bytes,
            f"{token}-image",
        )
        validate_image(image_file, settings.max_image_pixels)
        preview_path = create_public_preview(
            image_file,
            settings.upload_dir / "previews",
            f"{token}-preview",
        )
        raw_file = (
            await save_upload(
                raw,
                settings.upload_dir / "raw",
                settings.max_raw_bytes,
                f"{token}-raw",
            )
            if raw
            else None
        )
        raw_signature_valid = validate_raw(raw_file)
        result = await moderate(
            title=title,
            description=description,
            image_name=image_file.original_name,
            raw_name=raw_file.original_name if raw_file else None,
            image_hash=image_file.sha256,
            raw_hash=raw_file.sha256 if raw_file else None,
            raw_signature_valid=raw_signature_valid,
        )
        work = Work(
            owner_id=user.id,
            title=title.strip(),
            description=description.strip(),
            author_name=user.display_name,
            handle=user.handle,
            external_url=user.external_url,
            image_path=image_file.path,
            preview_path=preview_path,
            image_name=image_file.original_name,
            image_sha256=image_file.sha256,
            raw_path=raw_file.path if raw_file and raw_signature_valid else None,
            raw_name=raw_file.original_name if raw_file else None,
            raw_sha256=raw_file.sha256 if raw_file and raw_signature_valid else None,
            raw_verified=result.raw_verified,
            score=result.score,
            confidence=result.confidence,
            partition="review",
            moderation_status=result.status,
            moderation_summary=result.summary,
            risk_labels=",".join(result.labels),
        )
        work.partition = place_work(work)
        work.audit_events.extend(
            [
                AuditEvent(
                    kind="upload",
                    message=f"成片已保存；SHA-256 {image_file.sha256[:12]}…",
                    actor_id=str(user.id),
                ),
                AuditEvent(
                    kind="raw",
                    message=(
                        f"RAW 文件头验证通过；SHA-256 {raw_file.sha256[:12]}…"
                        if raw_file and raw_signature_valid
                        else "未上传 RAW 或 RAW 扩展名与文件头不匹配。"
                    ),
                    actor_id=str(user.id),
                ),
                AuditEvent(kind="moderation", message=result.summary, actor_id="ai-agent"),
            ]
        )
        ai_body = "AI 初评仅提供风险标签和质量初值；最终分区由盲评、可信评语和置信度更新。"
        metrics = analyze_review(ai_body)
        work.reviews.append(
            Review(
                reviewer_user_id=None,
                author_name="AI 初评（已标注）",
                body=ai_body,
                score=round(result.score),
                relevance=metrics.relevance,
                professional=metrics.professional,
                technical=metrics.technical,
                objective=metrics.objective,
                constructive=metrics.constructive,
                reviewer_trust=0.0,
                is_ai=True,
                folded=False,
                score_counted=False,
            )
        )
        if allow_preview:
            work.licenses.append(
                LicenseGrant(
                    license_type="preview",
                    price=preview_price,
                    terms="个人欣赏与收藏；禁止商用、转售和 AI 训练。",
                )
            )
        if allow_raw and result.raw_verified:
            work.licenses.append(
                LicenseGrant(
                    license_type="raw_study",
                    price=raw_price,
                    terms="仅学习研究；版权仍归作者；禁止二次传播。",
                )
            )
        db.add(work)
        db.commit()
        db.refresh(work)
        if raw_file and not raw_signature_valid:
            remove_files(raw_file.path)
        return work_out(work, principal, db)
    except Exception:
        remove_files(
            image_file.path if image_file else None,
            raw_file.path if raw_file else None,
            preview_path,
        )
        raise


@app.post("/v1/works/{work_id}/favorite", response_model=WorkOut)
def favorite_work(
    work_id: int,
    request: FavoriteRequest,
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> WorkOut:
    work = _get_visible_work(db, work_id, principal)
    existing = db.scalar(
        select(Favorite).where(
            Favorite.work_id == work.id,
            Favorite.user_id == principal.user_id,
        )
    )
    changed = False
    if request.favorite and existing is None:
        db.add(Favorite(work_id=work.id, user_id=principal.user_id))
        work.favorites += 1
        changed = True
    elif not request.favorite and existing is not None:
        db.delete(existing)
        work.favorites = max(0, work.favorites - 1)
        changed = True
    work.audit_events.append(
        AuditEvent(
            kind="favorite",
            message=(
                "收藏状态改变；社交数据不进入质量分。"
                if changed
                else "重复收藏请求被按幂等操作忽略。"
            ),
            actor_id=str(principal.user_id),
        )
    )
    db.commit()
    return work_out(work, principal, db)


@app.post("/v1/works/{work_id}/reviews", response_model=ReviewOut, status_code=201)
def create_review(
    work_id: int,
    request: ReviewCreate,
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> ReviewOut:
    work = _get_visible_work(db, work_id, principal)
    metrics = analyze_review(request.body)
    trust = reviewer_trust(db, principal.user_id)
    quality = max(0.0, min(1.0, metrics.average * 0.72 + trust * 0.28))
    extreme = request.score <= 10 or request.score >= 95
    self_review = work.owner_id == principal.user_id
    score_counted = (
        not self_review
        and quality >= 0.32
        and (not extreme or (len(request.body.strip()) >= 40 and metrics.average >= 0.45))
    )
    review = Review(
        work_id=work.id,
        reviewer_user_id=principal.user_id,
        author_name=principal.display_name,
        body=request.body.strip(),
        score=request.score,
        relevance=metrics.relevance,
        professional=metrics.professional,
        technical=metrics.technical,
        objective=metrics.objective,
        constructive=metrics.constructive,
        reviewer_trust=trust,
        folded=len(request.body.strip()) < 8 or quality < 0.32,
        score_counted=score_counted,
    )
    db.add(review)
    if score_counted:
        apply_review(work, request.score, metrics, trust)
    reason = (
        "作者自评不计入质量分。"
        if self_review
        else "评分已计入。"
        if score_counted
        else "低信息或无依据极端评分未计入。"
    )
    work.audit_events.append(
        AuditEvent(
            kind="review",
            message=f"评语主要类型 {metrics.strongest}；质量权重 {quality:.2f}；{reason}",
            actor_id=str(principal.user_id),
        )
    )
    db.commit()
    db.refresh(review)
    return review_out(review)


@app.get("/v1/blind/pair", response_model=BlindPairOut)
def blind_pair(
    sequence: int = Query(default=0, ge=0),
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> BlindPairOut:
    candidates = db.scalars(
        select(Work)
        .where(
            Work.raw_verified.is_(True),
            Work.moderation_status.in_(PUBLIC_STATUSES),
            Work.official_sample.is_(False),
            Work.preview_path.is_not(None),
            Work.owner_id != principal.user_id,
        )
        .order_by(Work.id)
    ).unique().all()
    if len(candidates) < 2:
        raise HTTPException(status_code=409, detail="Not enough eligible works")
    return BlindPairOut(
        left=blind_work_out(candidates[sequence % len(candidates)]),
        right=blind_work_out(candidates[(sequence + 1) % len(candidates)]),
        sequence=sequence,
    )


@app.post("/v1/blind/vote", response_model=MessageOut)
def blind_vote(
    request: BlindVoteRequest,
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> MessageOut:
    if request.left_work_id == request.right_work_id:
        raise HTTPException(status_code=400, detail="Pair must contain two works")
    left = _get_public_work(db, request.left_work_id)
    right = _get_public_work(db, request.right_work_id)
    if principal.user_id in {left.owner_id, right.owner_id}:
        raise HTTPException(status_code=403, detail="Owners cannot vote on their own works")
    if request.winner_work_id not in {None, left.id, right.id}:
        raise HTTPException(status_code=400, detail="Winner is outside pair")
    recent_cutoff = datetime.now(timezone.utc) - timedelta(hours=24)
    duplicate = db.scalar(
        select(VoteEvent).where(
            VoteEvent.voter_user_id == principal.user_id,
            VoteEvent.created_at >= recent_cutoff,
            or_(
                and_(VoteEvent.left_work_id == left.id, VoteEvent.right_work_id == right.id),
                and_(VoteEvent.left_work_id == right.id, VoteEvent.right_work_id == left.id),
            ),
        )
    )
    weight = 0.0 if duplicate else reviewer_trust(db, principal.user_id)
    if request.winner_work_id is not None and weight > 0:
        apply_blind_vote(left, request.winner_work_id == left.id, weight)
        apply_blind_vote(right, request.winner_work_id == right.id, weight)
    db.add(
        VoteEvent(
            voter_user_id=principal.user_id,
            left_work_id=left.id,
            right_work_id=right.id,
            winner_work_id=request.winner_work_id,
            weight=weight,
        )
    )
    db.commit()
    return MessageOut(message=f"Blind vote recorded with weight {weight:.2f}")


@app.post("/v1/works/{work_id}/appeals", response_model=MessageOut, status_code=201)
def appeal(
    work_id: int,
    request: AppealCreate,
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> MessageOut:
    work = _get_owned_work(db, work_id, principal)
    existing = db.scalar(
        select(Appeal).where(
            Appeal.work_id == work.id,
            Appeal.author_user_id == principal.user_id,
            Appeal.status == "pending",
        )
    )
    if existing:
        raise HTTPException(status_code=409, detail="An appeal is already pending")
    db.add(
        Appeal(
            work_id=work.id,
            author_user_id=principal.user_id,
            reason=request.reason.strip(),
        )
    )
    work.moderation_status = "appealing"
    work.partition = "archive"
    work.audit_events.append(
        AuditEvent(
            kind="appeal",
            message="作者提交申诉，等待管理员或高信誉评审复核。",
            actor_id=str(principal.user_id),
        )
    )
    db.commit()
    return MessageOut(message="Appeal queued")


@app.post("/v1/works/{work_id}/reports", response_model=MessageOut, status_code=201)
def report(
    work_id: int,
    request: ReportCreate,
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> MessageOut:
    work = _get_visible_work(db, work_id, principal)
    existing = db.scalar(
        select(Report).where(
            Report.work_id == work.id,
            Report.reporter_user_id == principal.user_id,
        )
    )
    if existing:
        raise HTTPException(status_code=409, detail="This work was already reported")
    db.add(
        Report(
            work_id=work.id,
            reporter_user_id=principal.user_id,
            reason=request.reason.strip(),
        )
    )
    work.audit_events.append(
        AuditEvent(
            kind="report",
            message="收到一项用户举报，等待复核。",
            actor_id=str(principal.user_id),
        )
    )
    db.commit()
    return MessageOut(message="Report accepted")


@app.post(
    "/v1/works/{work_id}/licenses/{license_type}",
    response_model=LicenseOut,
    status_code=201,
)
def purchase_license(
    work_id: int,
    license_type: str,
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> LicenseOut:
    work = _get_visible_work(db, work_id, principal)
    if work.official_sample:
        raise HTTPException(status_code=409, detail="Official samples are not licensable")
    if work.owner_id == principal.user_id:
        raise HTTPException(status_code=409, detail="Owners already control their files")
    offer = next(
        (
            item
            for item in work.licenses
            if item.license_type == license_type and item.buyer_user_id is None
        ),
        None,
    )
    if offer is None:
        raise HTTPException(status_code=404, detail="License unavailable")
    existing = db.scalar(
        select(LicenseGrant).where(
            LicenseGrant.work_id == work.id,
            LicenseGrant.license_type == license_type,
            LicenseGrant.buyer_user_id == principal.user_id,
            LicenseGrant.granted.is_(True),
        )
    )
    if existing:
        return license_out(existing)
    if offer.price > 0:
        raise HTTPException(
            status_code=402,
            detail="Payment provider is not configured; paid licenses cannot be granted",
        )
    grant = LicenseGrant(
        work_id=work.id,
        license_type=license_type,
        price=offer.price,
        terms=offer.terms,
        buyer_user_id=principal.user_id,
        granted=True,
        granted_at=datetime.now(timezone.utc),
    )
    db.add(grant)
    work.downloads += 1
    work.audit_events.append(
        AuditEvent(
            kind="license",
            message=f"生成 {license_type} 免费授权快照；版权仍归作者。",
            actor_id=str(principal.user_id),
        )
    )
    db.commit()
    db.refresh(grant)
    return license_out(grant)


@app.get("/v1/works/{work_id}/preview")
def preview(work_id: int, db: Session = Depends(get_db)) -> FileResponse:
    work = _get_public_work(db, work_id)
    if not work.preview_path or not Path(work.preview_path).exists():
        raise HTTPException(status_code=404, detail="Preview unavailable")
    return FileResponse(
        work.preview_path,
        media_type="image/jpeg",
        headers={"Cache-Control": "public, max-age=3600", "X-Content-Type-Options": "nosniff"},
    )


@app.get("/v1/works/{work_id}/original")
def original_download(
    work_id: int,
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> FileResponse:
    work = _get_visible_work(db, work_id, principal)
    _require_file_access(db, work, principal, "preview")
    if not work.image_path or not Path(work.image_path).exists():
        raise HTTPException(status_code=404, detail="Original image unavailable")
    return _private_file(work.image_path, work.image_name)


@app.get("/v1/works/{work_id}/raw")
def raw_download(
    work_id: int,
    principal: Principal = Depends(require_user),
    db: Session = Depends(get_db),
) -> FileResponse:
    work = _get_visible_work(db, work_id, principal)
    _require_file_access(db, work, principal, "raw_study")
    if not work.raw_path or not Path(work.raw_path).exists():
        raise HTTPException(status_code=404, detail="RAW unavailable")
    return _private_file(work.raw_path, work.raw_name)


@app.patch("/v1/admin/works/{work_id}", response_model=WorkOut)
def admin_moderate(
    work_id: int,
    request: AdminModerationUpdate,
    principal: Principal = Depends(require_admin),
    db: Session = Depends(get_db),
) -> WorkOut:
    work = _get_work(db, work_id)
    work.moderation_status = request.moderation_status
    work.moderation_summary = request.summary.strip()
    work.partition = place_work(work)
    work.audit_events.append(
        AuditEvent(kind="admin_moderation", message=request.summary.strip(), actor_id="admin")
    )
    db.commit()
    return work_out(work, principal, db)


def _normalize_handle(value: str) -> str:
    compact = "".join(ch for ch in value.strip().lower() if ch.isalnum() or ch in "._-")
    compact = compact.lstrip("@") or f"user-{uuid4().hex[:8]}"
    return f"@{compact[:100]}"


def _available_handle(db: Session, preferred: str) -> str:
    base = _normalize_handle(preferred)
    candidate = base
    index = 2
    while db.scalar(select(User.id).where(User.handle == candidate)) is not None:
        candidate = f"{base[:92]}-{index}"
        index += 1
    return candidate


def _get_user(db: Session, principal: Principal) -> User:
    if principal.user_id is None:
        raise HTTPException(status_code=403, detail="User session required")
    user = db.get(User, principal.user_id)
    if user is None or not user.is_active:
        raise HTTPException(status_code=401, detail="User session is no longer valid")
    return user


def _get_work(db: Session, work_id: int) -> Work:
    work = db.get(Work, work_id)
    if work is None:
        raise HTTPException(status_code=404, detail="Work not found")
    return work


def _is_public(work: Work) -> bool:
    return work.official_sample or (
        work.raw_verified and work.moderation_status in PUBLIC_STATUSES
    )


def _get_public_work(db: Session, work_id: int) -> Work:
    work = _get_work(db, work_id)
    if not _is_public(work):
        raise HTTPException(status_code=404, detail="Work not found")
    return work


def _get_visible_work(db: Session, work_id: int, principal: Principal | None) -> Work:
    work = _get_work(db, work_id)
    if _is_public(work):
        return work
    if principal and (
        principal.is_admin
        or (principal.user_id is not None and principal.user_id == work.owner_id)
    ):
        return work
    raise HTTPException(status_code=404, detail="Work not found")


def _get_owned_work(db: Session, work_id: int, principal: Principal) -> Work:
    work = _get_work(db, work_id)
    if not principal.is_admin and work.owner_id != principal.user_id:
        raise HTTPException(status_code=403, detail="Work owner required")
    return work


def _require_file_access(
    db: Session,
    work: Work,
    principal: Principal,
    license_type: str,
) -> None:
    if principal.is_admin or work.owner_id == principal.user_id:
        return
    grant = db.scalar(
        select(LicenseGrant).where(
            LicenseGrant.work_id == work.id,
            LicenseGrant.license_type == license_type,
            LicenseGrant.buyer_user_id == principal.user_id,
            LicenseGrant.granted.is_(True),
        )
    )
    if grant is None:
        raise HTTPException(status_code=403, detail=f"{license_type} license required")


def _private_file(path: str, filename: str | None) -> FileResponse:
    return FileResponse(
        path,
        filename=filename,
        headers={"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"},
    )
