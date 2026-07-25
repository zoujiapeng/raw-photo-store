from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from pathlib import Path

from fastapi import HTTPException, UploadFile, status
from PIL import Image, UnidentifiedImageError

RAW_EXTENSIONS = {".dng", ".cr2", ".cr3", ".nef", ".arw", ".rw2", ".orf", ".raf", ".pef"}
PUBLIC_IMAGE_FORMATS = {"JPEG", "PNG", "WEBP"}


@dataclass(frozen=True)
class StoredFile:
    path: str
    original_name: str
    sha256: str
    size: int


async def save_upload(
    upload: UploadFile,
    directory: Path,
    max_bytes: int,
    prefix: str,
) -> StoredFile:
    directory.mkdir(parents=True, exist_ok=True)
    original = upload.filename or f"{prefix}.bin"
    safe_name = re.sub(r"[^A-Za-z0-9._-]+", "_", original)[-180:]
    destination = directory / f"{prefix}-{safe_name}"
    digest = hashlib.sha256()
    size = 0
    try:
        with destination.open("wb") as handle:
            while chunk := await upload.read(1024 * 1024):
                size += len(chunk)
                if size > max_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail=f"{original} exceeds upload limit",
                    )
                digest.update(chunk)
                handle.write(chunk)
        if size == 0:
            raise HTTPException(status_code=400, detail=f"{original} is empty")
    except Exception:
        destination.unlink(missing_ok=True)
        raise
    finally:
        await upload.close()
    return StoredFile(str(destination), original, digest.hexdigest(), size)


def validate_image(stored: StoredFile, max_pixels: int) -> tuple[int, int, str]:
    try:
        with Image.open(stored.path) as image:
            image.verify()
        with Image.open(stored.path) as image:
            width, height = image.size
            image_format = (image.format or "").upper()
    except (UnidentifiedImageError, OSError, ValueError) as exc:
        Path(stored.path).unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="Display image is not decodable") from exc
    if image_format not in PUBLIC_IMAGE_FORMATS:
        Path(stored.path).unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="Display image must be JPEG, PNG, or WebP")
    if width <= 0 or height <= 0 or width * height > max_pixels:
        Path(stored.path).unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="Display image dimensions are not allowed")
    return width, height, image_format


def create_public_preview(stored: StoredFile, directory: Path, prefix: str) -> str:
    directory.mkdir(parents=True, exist_ok=True)
    destination = directory / f"{prefix}.jpg"
    try:
        with Image.open(stored.path) as source:
            source.thumbnail((2400, 2400))
            if source.mode not in {"RGB", "L"}:
                canvas = Image.new("RGB", source.size, "black")
                if "A" in source.getbands():
                    canvas.paste(source, mask=source.getchannel("A"))
                else:
                    canvas.paste(source.convert("RGB"))
                output = canvas
            else:
                output = source.convert("RGB")
            output.save(destination, "JPEG", quality=88, optimize=True)
    except (UnidentifiedImageError, OSError, ValueError) as exc:
        destination.unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="Could not create public preview") from exc
    return str(destination)


def validate_raw(stored: StoredFile | None) -> bool:
    if stored is None:
        return False
    extension = Path(stored.original_name).suffix.lower()
    if extension not in RAW_EXTENSIONS:
        return False
    try:
        header = Path(stored.path).read_bytes()[:32]
    except OSError:
        return False
    tiff = header.startswith(b"II*\x00") or header.startswith(b"MM\x00*")
    signatures = {
        ".dng": tiff,
        ".cr2": tiff and b"CR" in header,
        ".cr3": b"ftypcrx" in header or b"ftypcr3" in header,
        ".nef": tiff,
        ".arw": tiff,
        ".rw2": header.startswith(b"IIU\x00"),
        ".orf": header.startswith((b"IIRO", b"MMOR")),
        ".raf": header.startswith(b"FUJIFILMCCD-RAW"),
        ".pef": tiff,
    }
    return signatures.get(extension, False)


def remove_files(*paths: str | None) -> None:
    for path in paths:
        if path:
            Path(path).unlink(missing_ok=True)
