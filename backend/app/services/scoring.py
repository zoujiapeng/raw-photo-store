from datetime import datetime,timedelta,timezone
from sqlalchemy import func,select
from sqlalchemy.orm import Session
from ..models import Review,VoteEvent,Work
from .review_quality import Metrics
def place_work(w):
    if w.moderation_status in {'needs_raw','rejected','appealing'}: return 'archive'
    if w.raw_verified and w.score>=84 and w.confidence>=.70:return 'gallery'
    if w.raw_verified and w.score>=64:return 'review'
    if w.score>=45:return 'workshop'
    return 'archive'
def reviewer_trust(db:Session,reviewer_id:str,work_id:int|None=None):
    count=db.scalar(select(func.count(Review.id)).where(Review.reviewer_id==reviewer_id,Review.score_counted.is_(True))) or 0
    return min(.72,.22+count*.015)
def apply_review(w:Work,score:int,m:Metrics,trust:float):
    q=max(0,min(1,m.average*.72+trust*.28)); influence=max(.004,min(.045,.004+trust*q*.045)); w.score=max(1,min(99.9,w.score*(1-influence)+score*influence)); w.confidence=min(.98,w.confidence+.010+q*.020); w.ratings+=1; w.partition=place_work(w)
def apply_blind_vote(w:Work,won:bool,weight:float):
    w.score=max(1,min(99.9,w.score+(1.25*weight if won else -.72*weight))); w.confidence=min(.98,w.confidence+.010); w.ratings+=1; w.partition=place_work(w)
