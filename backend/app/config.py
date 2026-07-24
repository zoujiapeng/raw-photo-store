from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    database_url: str = os.getenv("DATABASE_URL", "sqlite:///./data/rawjudge.db")
    upload_dir: Path = Path(os.getenv("UPLOAD_DIR", "./uploads"))
    cors_origins: str = os.getenv("CORS_ORIGINS", "*")
    openai_api_key: str | None = os.getenv("OPENAI_API_KEY") or None
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-5.6-luna")
    openai_base_url: str = os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    content_policy_profile: str = os.getenv("CONTENT_POLICY_PROFILE", "global")
    max_image_bytes: int = int(os.getenv("MAX_IMAGE_BYTES", str(25 * 1024 * 1024)))
    max_raw_bytes: int = int(os.getenv("MAX_RAW_BYTES", str(300 * 1024 * 1024)))


settings = Settings()
settings.upload_dir.mkdir(parents=True, exist_ok=True)
Path("./data").mkdir(parents=True, exist_ok=True)
