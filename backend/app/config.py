from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    app_env: str = os.getenv("APP_ENV", "development")
    database_url: str = os.getenv("DATABASE_URL", "sqlite:///./data/rawjudge.db")
    upload_dir: Path = Path(os.getenv("UPLOAD_DIR", "./uploads"))
    public_base_url: str = os.getenv("PUBLIC_BASE_URL", "").rstrip("/")
    trusted_hosts: str = os.getenv("TRUSTED_HOSTS", "*")
    cors_origins: str = os.getenv(
        "CORS_ORIGINS", "http://localhost:3000,http://localhost:5173"
    )
    token_pepper: str = os.getenv(
        "TOKEN_PEPPER", "rawjudge-local-development-pepper"
    )
    admin_token: str | None = os.getenv("ADMIN_TOKEN") or None
    allow_registration: bool = _bool("ALLOW_REGISTRATION", True)
    seed_demo_data: bool = _bool("SEED_DEMO_DATA", True)
    openai_api_key: str | None = os.getenv("OPENAI_API_KEY") or None
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-5.6-luna")
    openai_base_url: str = os.getenv(
        "OPENAI_BASE_URL", "https://api.openai.com/v1"
    )
    content_policy_profile: str = os.getenv("CONTENT_POLICY_PROFILE", "global")
    max_image_bytes: int = int(
        os.getenv("MAX_IMAGE_BYTES", str(25 * 1024 * 1024))
    )
    max_raw_bytes: int = int(
        os.getenv("MAX_RAW_BYTES", str(300 * 1024 * 1024))
    )
    max_image_pixels: int = int(os.getenv("MAX_IMAGE_PIXELS", "80000000"))
    max_page_size: int = int(os.getenv("MAX_PAGE_SIZE", "100"))

    @property
    def production(self) -> bool:
        return self.app_env.lower() == "production"

    def validate(self) -> None:
        if not self.production:
            return
        if len(self.token_pepper) < 32 or "development" in self.token_pepper:
            raise RuntimeError("TOKEN_PEPPER must be a random 32+ character secret")
        if "*" in {item.strip() for item in self.trusted_hosts.split(",")}:
            raise RuntimeError("TRUSTED_HOSTS cannot contain * in production")
        if "*" in {item.strip() for item in self.cors_origins.split(",")}:
            raise RuntimeError("CORS_ORIGINS cannot contain * in production")
        if self.public_base_url and not self.public_base_url.startswith("https://"):
            raise RuntimeError("PUBLIC_BASE_URL must use HTTPS in production")


settings = Settings()
settings.validate()
settings.upload_dir.mkdir(parents=True, exist_ok=True)
Path("./data").mkdir(parents=True, exist_ok=True)
