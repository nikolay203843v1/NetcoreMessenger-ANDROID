from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.database import get_db
from app.auth.dependencies import get_current_user
from app.users.service import UserService
from app.users.models import User

router = APIRouter(prefix="/users", tags=["users"])


from datetime import datetime


from pydantic import BaseModel, Field, field_serializer

class UserOut(BaseModel):
    id: int
    phone: str | None = None
    google_id: str | None = None
    username: str | None = None
    display_name: str
    bio: str = ""
    avatar_media_id: int | None = None
    status: str = "offline"
    last_online_at: datetime | None = None
    created_at: datetime

    model_config = {"from_attributes": True}

    @field_serializer("created_at", "last_online_at")
    def serialize_dt(self, dt: datetime | None) -> int | None:
        return int(dt.timestamp() * 1000) if dt else None


class UpdateProfileRequest(BaseModel):
    username: str | None = None
    display_name: str | None = None
    bio: str | None = None
    avatar_media_id: int | None = None


class AddProfilePhotoRequest(BaseModel):
    media_id: int


class ProfilePhotoOut(BaseModel):
    id: int
    media_id: int
    position: int
    created_at: datetime

    model_config = {"from_attributes": True}

    @field_serializer("created_at")
    def serialize_dt(self, dt: datetime | None) -> int | None:
        return int(dt.timestamp() * 1000) if dt else None


@router.get("/me", response_model=UserOut)
async def get_me(
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = UserService(db)
    user = await service.get_user(current_user["user_id"])
    return user


@router.patch("/me", response_model=UserOut)
async def update_me(
    body: UpdateProfileRequest,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = UserService(db)
    data = body.model_dump(exclude_none=True)
    user = await service.update_profile(current_user["user_id"], data)
    return user


@router.get("/me/photos", response_model=list[ProfilePhotoOut])
async def get_my_profile_photos(
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = UserService(db)
    return await service.get_profile_photos(current_user["user_id"])


@router.post("/me/photos", response_model=ProfilePhotoOut)
async def add_my_profile_photo(
    body: AddProfilePhotoRequest,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = UserService(db)
    return await service.add_profile_photo(current_user["user_id"], body.media_id)


@router.delete("/me/photos/{photo_id}", response_model=list[ProfilePhotoOut])
async def delete_my_profile_photo(
    photo_id: int,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = UserService(db)
    return await service.delete_profile_photo(current_user["user_id"], photo_id)


@router.get("/{user_id}/photos", response_model=list[ProfilePhotoOut])
async def get_user_profile_photos(
    user_id: int,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = UserService(db)
    return await service.get_profile_photos(user_id)


@router.get("/search", response_model=list[UserOut])
async def search_users(
    q: str = Query(..., min_length=1, max_length=64),
    limit: int = Query(20, ge=1, le=100),
    offset: int = Query(0, ge=0),
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = UserService(db)
    users = await service.search(q, limit, offset)
    # Не возвращаем себя в результатах
    return [u for u in users if u.id != current_user["user_id"]]


@router.get("/check-username/{username}")
async def check_username(
    username: str,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    from sqlalchemy import select as _select
    from app.users.models import User as _User
    result = await db.execute(
        _select(_User).where(_User.username == username, _User.is_deleted == False)
    )
    existing = result.scalar_one_or_none()
    # Своё имя считаем "доступным"
    available = existing is None or existing.id == current_user["user_id"]
    return {"available": available}


@router.get("/{user_id}", response_model=UserOut)
async def get_user(
    user_id: int,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = UserService(db)
    return await service.get_user(user_id)
