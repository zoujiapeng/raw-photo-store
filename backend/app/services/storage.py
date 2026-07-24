from __future__ import annotations
import hashlib, re
from dataclasses import dataclass
from pathlib import Path
from fastapi import HTTPException, UploadFile, status
@dataclass(frozen=True)
class StoredFile: path:str; original_name:str; sha256:str; size:int
async def save_upload(upload: UploadFile, directory: Path, max_bytes: int, prefix: str) -> StoredFile:
    directory.mkdir(parents=True, exist_ok=True); original=upload.filename or f"{prefix}.bin"; safe_name=re.sub(r"[^A-Za-z0-9._-]+","_",original)[-180:]; destination=directory/f"{prefix}-{safe_name}"; digest=hashlib.sha256(); size=0
    try:
        with destination.open("wb") as handle:
            while chunk:=await upload.read(1024*1024):
                size+=len(chunk)
                if size>max_bytes: raise HTTPException(status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,detail=f"{original} exceeds upload limit")
                digest.update(chunk); handle.write(chunk)
    except Exception: destination.unlink(missing_ok=True); raise
    finally: await upload.close()
    return StoredFile(str(destination),original,digest.hexdigest(),size)
