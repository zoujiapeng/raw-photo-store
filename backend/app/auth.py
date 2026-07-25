from __future__ import annotations

import hashlib
import secrets
from dataclasses import dataclass

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from .config import settings
from .database import get_db
from .models import User

bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class Principal:
    user_id: int | None
    display_name: str
    handle: str
    is_admin: bool = False


def token_hash(token: str) -> str:
    return hashlib.sha256(f"{settings.token_pepper}:{token}".encode()).hexdigest()


def issue_token() -> tuple[str, str]:
    token = f"rj_{secrets.token_urlsafe(32)}"
    return token, token_hash(token)


def _resolve(
    credentials: HTTPAuthorizationCredentials | None,
    db: Session,
) -> Principal | None:
    if credentials is None or credentials.scheme.lower() != "bearer":
        return None
    token = credentials.credentials
    if settings.admin_token and secrets.compare_digest(token, settings.admin_token):
        return Principal(None, "RAWJudge 管理员", "@rawjudge.admin", True)
    user = db.scalar(select(User).where(User.token_hash == token_hash(token)))
    if user is None or not user.is_active:
        return None
    return Principal(user.id, user.display_name, user.handle, user.is_admin)


def optional_principal(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> Principal | None:
    return _resolve(credentials, db)


def require_principal(
    principal: Principal | None = Depends(optional_principal),
) -> Principal:
    if principal is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Valid Bearer token required",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return principal


def require_user(principal: Principal = Depends(require_principal)) -> Principal:
    if principal.user_id is None:
        raise HTTPException(status_code=403, detail="A user session is required")
    return principal


def require_admin(principal: Principal = Depends(require_principal)) -> Principal:
    if not principal.is_admin:
        raise HTTPException(status_code=403, detail="Administrator required")
    return principal
