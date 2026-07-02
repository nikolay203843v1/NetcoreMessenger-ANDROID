import os
import hashlib
import uuid
import logging
from pathlib import Path

from fastapi import APIRouter, Depends, File, UploadFile, HTTPException
from fastapi.responses import FileResponse
from PIL import Image, ImageOps
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.database import get_db
from app.common.errors import NotFoundError
from app.auth.dependencies import get_current_user
from app.media.models import Media
from app.chats.models import Chat, ChatParticipant
from app.messages.models import Message
from app.users.models import User, UserProfilePhoto

logger = logging.getLogger("netcore.media")

router = APIRouter(prefix="/media", tags=["media"])

UPLOAD_DIR = Path(os.getenv("UPLOAD_DIR", "./uploads")).resolve()
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

ALLOWED_IMAGE_MIME = {"image/jpeg", "image/png", "image/webp", "image/gif"}
ALLOWED_VIDEO_MIME = {"video/mp4", "video/webm", "video/quicktime"}
ALLOWED_AUDIO_MIME = {"audio/mpeg", "audio/ogg", "audio/mp4", "audio/aac", "audio/webm"}
MAX_SIZE_BYTES = 25 * 1024 * 1024
THUMB_MAX_SIDE = 512
THUMB_QUALITY = 78


def _kind_from_mime(mime: str) -> str:
    if mime in ALLOWED_IMAGE_MIME:
        return "image"
    if mime in ALLOWED_VIDEO_MIME:
        return "video"
    if mime in ALLOWED_AUDIO_MIME:
        return "audio"
    return "document"


def _media_to_dict(media: Media) -> dict:
    return {
        "id": media.id,
        "type": media.type,
        "mime_type": media.mime_type,
        "size_bytes": media.size_bytes,
        "width": media.width,
        "height": media.height,
        "thumbnail_media_id": media.thumbnail_media_id,
        "url": f"/api/v1/media/{media.id}",
        "thumbnail_url": f"/api/v1/media/{media.id}/thumbnail",
    }


def _image_dimensions(path: Path) -> tuple[int | None, int | None]:
    try:
        with Image.open(path) as img:
            return img.size
    except Exception:
        logger.exception("Failed to read image dimensions: %s", path)
        return None, None


def _create_image_thumbnail(path: Path, user_id: int) -> tuple[Media | None, int | None, int | None]:
    try:
        with Image.open(path) as img:
            img = ImageOps.exif_transpose(img)
            width, height = img.size
            img.thumbnail((THUMB_MAX_SIDE, THUMB_MAX_SIDE), Image.Resampling.LANCZOS)
            if img.mode not in ("RGB", "L"):
                img = img.convert("RGB")
            thumb_width, thumb_height = img.size

            thumb_path = UPLOAD_DIR / f"{uuid.uuid4().hex}_thumb.jpg"
            img.save(thumb_path, "JPEG", quality=THUMB_QUALITY, optimize=True)

        thumb_bytes = thumb_path.read_bytes()
        thumb = Media(
            user_id=user_id,
            type="image",
            file_name=f"{path.stem}_thumb.jpg"[:256],
            mime_type="image/jpeg",
            sha256=hashlib.sha256(thumb_bytes).hexdigest(),
            size_bytes=len(thumb_bytes),
            width=thumb_width,
            height=thumb_height,
            storage_path=str(thumb_path),
            storage_bucket="local",
            is_compressed=True,
            is_ready=True,
        )
        return thumb, width, height
    except Exception:
        logger.exception("Failed to create thumbnail: %s", path)
        return None, None, None


@router.post("/upload", status_code=201)
async def upload_media(
    file: UploadFile = File(...),
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if not file.filename:
        raise HTTPException(status_code=400, detail={"code": "BAD_FILE", "message": "Missing filename"})

    hasher = hashlib.sha256()
    mime = file.content_type or "application/octet-stream"
    kind = _kind_from_mime(mime)
    ext = Path(file.filename).suffix.lower()
    temp_path = UPLOAD_DIR / f".upload-{uuid.uuid4().hex}{ext}"
    size = 0

    try:
        with temp_path.open("wb") as out:
            while True:
                chunk = await file.read(1024 * 1024)
                if not chunk:
                    break
                size += len(chunk)
                if size > MAX_SIZE_BYTES:
                    raise HTTPException(status_code=413, detail={"code": "TOO_LARGE", "message": "File too large"})
                hasher.update(chunk)
                out.write(chunk)

        if size == 0:
            raise HTTPException(status_code=400, detail={"code": "EMPTY_FILE", "message": "Empty file"})

        sha = hasher.hexdigest()

        existing = await db.execute(
            select(Media).where(Media.sha256 == sha, Media.user_id == current_user["user_id"])
        )
        existing = existing.scalar_one_or_none()
        if existing:
            if existing.type == "image" and not existing.thumbnail_media_id:
                thumb, width, height = _create_image_thumbnail(Path(existing.storage_path), current_user["user_id"])
                if thumb:
                    db.add(thumb)
                    await db.flush()
                    existing.thumbnail_media_id = thumb.id
                    existing.width = existing.width or width
                    existing.height = existing.height or height
            return _media_to_dict(existing)

        storage_path = UPLOAD_DIR / f"{uuid.uuid4().hex}{ext}"
        temp_path.replace(storage_path)
        width = None
        height = None
        thumb = None
        if kind == "image":
            thumb, width, height = _create_image_thumbnail(storage_path, current_user["user_id"])
            if thumb:
                db.add(thumb)
                await db.flush()

        media = Media(
            user_id=current_user["user_id"],
            type=kind,
            file_name=file.filename[:256],
            mime_type=mime[:64],
            sha256=sha,
            size_bytes=size,
            width=width,
            height=height,
            thumbnail_media_id=thumb.id if thumb else None,
            storage_path=str(storage_path),
            storage_bucket="local",
            is_ready=True,
        )
        db.add(media)
        await db.flush()
        logger.info("Uploaded media id=%s user=%s size=%s mime=%s", media.id, current_user["user_id"], size, mime)
        return _media_to_dict(media)
    finally:
        if temp_path.exists():
            temp_path.unlink(missing_ok=True)


async def _can_access_media(db: AsyncSession, media: Media, user_id: int) -> bool:
    """Разрешаем доступ если:
      1) пользователь — владелец media;
      2) media — аватар или фото профиля любого пользователя (публично для авторизованных);
      3) media — аватар чата, где пользователь состоит;
      4) media — thumbnail от media, который прошёл (1)-(3).
    """
    if media.user_id == user_id:
        return True
    # 2) User.avatar_media_id / UserProfilePhoto.media_id
    q = await db.execute(select(User.id).where(User.avatar_media_id == media.id).limit(1))
    if q.scalar_one_or_none() is not None:
        return True
    q = await db.execute(select(UserProfilePhoto.id).where(UserProfilePhoto.media_id == media.id).limit(1))
    if q.scalar_one_or_none() is not None:
        return True
    # 3) Chat.avatar_media_id — с проверкой участия
    q = await db.execute(select(Chat.id).where(Chat.avatar_media_id == media.id))
    for (chat_id,) in q.all():
        p = await db.execute(
            select(ChatParticipant.id)
            .where(
                ChatParticipant.chat_id == chat_id,
                ChatParticipant.user_id == user_id,
                ChatParticipant.left_at.is_(None),
            )
            .limit(1)
        )
        if p.scalar_one_or_none() is not None:
            return True
    # 3.5) Media attached to a message in a chat where the user is still a participant.
    q = await db.execute(
        select(Message.id)
        .join(ChatParticipant, ChatParticipant.chat_id == Message.chat_id)
        .where(
            Message.content == str(media.id),
            Message.type.in_(("image", "video", "voice", "circle", "document")),
            Message.is_deleted.is_(False),
            ChatParticipant.user_id == user_id,
            ChatParticipant.left_at.is_(None),
        )
        .limit(1)
    )
    if q.scalar_one_or_none() is not None:
        return True
    # 4) Thumbnail parent
    q = await db.execute(select(Media).where(Media.thumbnail_media_id == media.id).limit(1))
    parent = q.scalar_one_or_none()
    if parent is not None:
        return await _can_access_media(db, parent, user_id)
    return False


@router.get("/{media_id}")
async def get_media(
    media_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    res = await db.execute(select(Media).where(Media.id == media_id))
    media = res.scalar_one_or_none()
    if not media:
        raise NotFoundError("Media not found")
    if not await _can_access_media(db, media, current_user["user_id"]):
        raise HTTPException(status_code=403, detail={"code": "FORBIDDEN", "message": "No access to media"})
    p = Path(media.storage_path)
    if not p.exists():
        raise NotFoundError("File missing")
    return FileResponse(path=str(p), media_type=media.mime_type, filename=media.file_name)


@router.get("/{media_id}/thumbnail")
async def get_media_thumbnail(
    media_id: int,
    db: AsyncSession = Depends(get_db),
    current_user: dict = Depends(get_current_user),
):
    res = await db.execute(select(Media).where(Media.id == media_id))
    media = res.scalar_one_or_none()
    if not media:
        raise NotFoundError("Media not found")
    if not await _can_access_media(db, media, current_user["user_id"]):
        raise HTTPException(status_code=403, detail={"code": "FORBIDDEN", "message": "No access to media"})

    served = media
    if media.thumbnail_media_id:
        thumb_res = await db.execute(select(Media).where(Media.id == media.thumbnail_media_id))
        served = thumb_res.scalar_one_or_none() or media

    p = Path(served.storage_path)
    if not p.exists():
        fallback = Path(media.storage_path)
        if fallback.exists():
            served = media
            p = fallback
        else:
            raise NotFoundError("File missing")

    return FileResponse(path=str(p), media_type=served.mime_type, filename=served.file_name)
