from datetime import datetime
from sqlalchemy import Integer, String, DateTime, Boolean, LargeBinary
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.sql import func

from app.common.database import Base


class IdentityKey(Base):
    __tablename__ = "identity_keys"

    user_id: Mapped[int] = mapped_column(Integer, primary_key=True)
    identity_public: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    identity_private_encrypted: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())


class PreKey(Base):
    __tablename__ = "pre_keys"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(Integer, nullable=False)
    key_id: Mapped[int] = mapped_column(Integer, nullable=False)
    public_key: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    private_key_encrypted: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    is_used: Mapped[bool] = mapped_column(Boolean, default=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())


class Session(Base):
    __tablename__ = "sessions"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(Integer, nullable=False)
    contact_id: Mapped[int] = mapped_column(Integer, nullable=False)
    session_data_encrypted: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now(), onupdate=func.now())
