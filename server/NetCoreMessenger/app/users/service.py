from sqlalchemy import select, or_
from sqlalchemy.ext.asyncio import AsyncSession

from app.users.models import User, UserProfilePhoto
from app.users.validators import validate_username
from app.common.errors import NotFoundError


class UserService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def get_user(self, user_id: int) -> User:
        result = await self.db.execute(
            select(User).where(User.id == user_id, User.is_deleted == False)
        )
        user = result.scalar_one_or_none()
        if not user:
            raise NotFoundError("User not found")
        return user

    async def update_profile(self, user_id: int, data: dict) -> User:
        user = await self.get_user(user_id)
        old_avatar_media_id = user.avatar_media_id

        if "username" in data:
            err = validate_username(data["username"])
            if err:
                raise ValueError(err)

        for field in ("username", "display_name", "bio", "avatar_media_id"):
            if field in data:
                setattr(user, field, data[field])

        if data.get("avatar_media_id") is not None:
            await self.add_profile_photo(
                user_id,
                int(data["avatar_media_id"]),
                seed_media_id=old_avatar_media_id,
            )

        await self.db.flush()
        await self.db.refresh(user)
        return user

    async def get_profile_photos(self, user_id: int) -> list[UserProfilePhoto]:
        user = await self.get_user(user_id)
        await self._normalize_profile_photo_order(user)
        photos = await self._list_profile_photos(user_id)
        if not photos and user.avatar_media_id is not None:
            photo = UserProfilePhoto(
                user_id=user_id,
                media_id=int(user.avatar_media_id),
                position=0,
            )
            self.db.add(photo)
            await self.db.flush()
            photos = [photo]
        return photos

    async def delete_profile_photo(self, user_id: int, photo_id: int) -> list[UserProfilePhoto]:
        user = await self.get_user(user_id)
        result = await self.db.execute(
            select(UserProfilePhoto).where(
                UserProfilePhoto.id == photo_id,
                UserProfilePhoto.user_id == user_id,
            )
        )
        photo = result.scalar_one_or_none()
        if not photo:
            raise NotFoundError("Photo not found")

        deleted_media_id = photo.media_id
        await self.db.delete(photo)
        await self.db.flush()

        photos = await self._list_profile_photos(user_id)
        for index, item in enumerate(photos):
            item.position = index

        if user.avatar_media_id == deleted_media_id:
            user.avatar_media_id = photos[0].media_id if photos else None

        await self.db.flush()
        await self.db.refresh(user)
        return await self.get_profile_photos(user_id)

    async def _list_profile_photos(self, user_id: int) -> list[UserProfilePhoto]:
        result = await self.db.execute(
            select(UserProfilePhoto)
            .where(UserProfilePhoto.user_id == user_id)
            .order_by(UserProfilePhoto.position.asc(), UserProfilePhoto.id.desc())
        )
        return list(result.scalars().all())

    async def _normalize_profile_photo_order(self, user: User) -> None:
        photos = await self._list_profile_photos(user.id)
        if not photos:
            return

        ordered = photos
        if user.avatar_media_id is not None:
            current = next((p for p in photos if p.media_id == user.avatar_media_id), None)
            if current:
                ordered = [current] + [p for p in photos if p.id != current.id]

        changed = False
        for index, photo in enumerate(ordered):
            if photo.position != index:
                photo.position = index
                changed = True
        if changed:
            await self.db.flush()

    async def add_profile_photo(
        self,
        user_id: int,
        media_id: int,
        seed_media_id: int | None = None,
    ) -> UserProfilePhoto:
        existing = await self.db.execute(
            select(UserProfilePhoto).where(
                UserProfilePhoto.user_id == user_id,
                UserProfilePhoto.media_id == media_id,
            )
        )
        photo = existing.scalar_one_or_none()
        if photo:
            return photo

        photos = await self._list_profile_photos(user_id)
        if not photos and seed_media_id is not None and seed_media_id != media_id:
            seeded = UserProfilePhoto(
                user_id=user_id,
                media_id=int(seed_media_id),
                position=0,
            )
            self.db.add(seeded)
            await self.db.flush()
            photos = [seeded]

        for existing_photo in photos:
            existing_photo.position += 1

        photo = UserProfilePhoto(
            user_id=user_id,
            media_id=media_id,
            position=0,
        )
        self.db.add(photo)
        await self.db.flush()
        return photo

    async def search(self, query: str, limit: int = 20, offset: int = 0) -> list[User]:
        pattern = f"%{query}%"
        result = await self.db.execute(
            select(User)
            .where(
                User.is_deleted == False,
                or_(
                    User.username.ilike(pattern),
                    User.display_name.ilike(pattern),
                    User.phone.ilike(pattern),
                ),
            )
            .limit(limit)
            .offset(offset)
        )
        return list(result.scalars().all())

    async def set_online(self, user_id: int):
        user = await self.get_user(user_id)
        user.status = "online"
        await self.db.flush()

    async def set_offline(self, user_id: int):
        from datetime import datetime, timezone
        user = await self.get_user(user_id)
        user.status = "offline"
        user.last_online_at = datetime.now(timezone.utc)
        await self.db.flush()
