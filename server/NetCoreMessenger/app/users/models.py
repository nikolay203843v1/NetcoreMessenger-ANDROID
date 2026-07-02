from datetime import datetime
from sqlalchemy import Integer, String, DateTime, BigInteger, Boolean, Text, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.sql import func

from app.common.database import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    google_id: Mapped[str | None] = mapped_column(String(64), unique=True, nullable=True, index=True)
    phone: Mapped[str | None] = mapped_column(String(20), unique=True, nullable=True, index=True)
    username: Mapped[str | None] = mapped_column(String(32), unique=True, nullable=True)
    display_name: Mapped[str] = mapped_column(String(64), nullable=False)
    bio: Mapped[str] = mapped_column(String(512), default="")
    avatar_media_id: Mapped[int | None] = mapped_column(BigInteger, ForeignKey("media.id", ondelete="SET NULL"), nullable=True)
    status: Mapped[str] = mapped_column(String(16), default="offline")
    last_online_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    is_deleted: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now(), onupdate=func.now())


class UserProfilePhoto(Base):
    __tablename__ = "user_profile_photos"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    media_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("media.id", ondelete="CASCADE"), nullable=False)
    position: Mapped[int] = mapped_column(Integer, default=0)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
