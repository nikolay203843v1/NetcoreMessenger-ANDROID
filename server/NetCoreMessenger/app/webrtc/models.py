from datetime import datetime
from sqlalchemy import Integer, String, DateTime, ForeignKey
from sqlalchemy.orm import Mapped, mapped_column
from sqlalchemy.sql import func

from app.common.database import Base


class CallSession(Base):
    __tablename__ = "calls"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    caller_id: Mapped[int] = mapped_column(Integer, ForeignKey("users.id"), nullable=False)
    callee_id: Mapped[int] = mapped_column(Integer, ForeignKey("users.id"), nullable=False)
    chat_id: Mapped[int | None] = mapped_column(Integer, ForeignKey("chats.id"), nullable=True)
    call_type: Mapped[str] = mapped_column(String(8), nullable=False)
    state: Mapped[str] = mapped_column(String(16), default="calling")
    started_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
    ended_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    duration_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)
