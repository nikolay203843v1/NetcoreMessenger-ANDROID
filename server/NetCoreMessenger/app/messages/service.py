import uuid
from datetime import datetime, timezone
from sqlalchemy import select, update, delete, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.utils import generate_sort_key
from app.messages.models import Message, MessageStatus, MessageEdit
from app.chats.service import ChatService
from app.common.errors import NotFoundError, ForbiddenError
from app.websocket.manager import connection_registry
from app.websocket.rooms import room_registry

MAX_EDIT_HISTORY = 10
STATUS_PRIORITY = {"sent": 0, "delivered": 1, "read": 2}


def _format_duration(duration_ms: int | None) -> str:
    total_seconds = max((duration_ms or 0) // 1000, 0)
    minutes, seconds = divmod(total_seconds, 60)
    hours, minutes = divmod(minutes, 60)
    if hours > 0:
        return f"{hours}:{minutes:02d}:{seconds:02d}"
    return f"{minutes}:{seconds:02d}"


class MessageService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.chats = ChatService(db)

    async def send_message(
        self,
        sender_id: int,
        chat_id: int,
        content: str,
        msg_type: str = "text",
        client_id: str | None = None,
        reply_to_msg_id: int | None = None,
        album_id: str | None = None,
    ) -> tuple[Message, bool]:
        participant = await self.chats.get_participant(chat_id, sender_id)
        if not participant:
            raise ForbiddenError("Not a participant in this chat")

        if client_id is None:
            client_id = str(uuid.uuid4())

        existing = await self._find_by_client_id(chat_id, client_id)
        if existing:
            return existing, False

        sort_key = generate_sort_key()

        msg = Message(
            client_id=client_id,
            chat_id=chat_id,
            sender_id=sender_id,
            type=msg_type,
            content=content,
            album_id=album_id,
            reply_to_msg_id=reply_to_msg_id,
            sort_key=sort_key,
        )
        self.db.add(msg)
        await self.db.flush()

        participants = await self.chats.get_participants(chat_id)
        for uid in participants:
            if uid != sender_id:
                ms = MessageStatus(message_id=msg.id, user_id=uid, status="sent")
                self.db.add(ms)

        await self.db.flush()
        await self.db.refresh(msg)
        return msg, True

    async def _find_by_client_id(self, chat_id: int, client_id: str) -> Message | None:
        result = await self.db.execute(
            select(Message).where(
                Message.chat_id == chat_id,
                Message.client_id == client_id,
                Message.is_deleted == False,
            )
        )
        return result.scalar_one_or_none()

    async def edit_message(self, message_id: int, user_id: int, new_content: str) -> Message:
        msg = await self._get_message(message_id)
        if msg.sender_id != user_id:
            raise ForbiddenError("Cannot edit another user's message")
        if msg.is_deleted:
            raise NotFoundError("Message deleted")

        edit_count = await self.db.scalar(
            select(MessageEdit).where(MessageEdit.message_id == message_id)
        )

        if edit_count and edit_count >= MAX_EDIT_HISTORY:
            oldest = await self.db.execute(
                select(MessageEdit)
                .where(MessageEdit.message_id == message_id)
                .order_by(MessageEdit.edited_at.asc())
                .limit(1)
            )
            await self.db.delete(oldest.scalar_one())

        edit = MessageEdit(message_id=message_id, content_old=msg.content)
        self.db.add(edit)
        msg.content = new_content
        msg.edited_at = datetime.now(timezone.utc)
        await self.db.flush()
        await self.db.refresh(msg)
        return msg

    async def delete_message(self, message_id: int, user_id: int):
        msg = await self._get_message(message_id)
        if msg.sender_id != user_id:
            raise ForbiddenError("Cannot delete another user's message")
        msg.is_deleted = True
        await self.db.flush()

    async def forward_message(self, message_id: int, user_id: int, target_chat_id: int) -> Message:
        original = await self._get_message(message_id)
        return await self.send_message(
            sender_id=user_id,
            chat_id=target_chat_id,
            content=original.content,
            msg_type=original.type,
            reply_to_msg_id=None,
        )

    async def mark_delivered(self, message_id: int, user_id: int):
        result = await self.db.execute(
            select(MessageStatus).where(
                MessageStatus.message_id == message_id,
                MessageStatus.user_id == user_id,
            )
        )
        ms = result.scalar_one_or_none()
        if ms and ms.status == "sent":
            ms.status = "delivered"
            ms.status_at = datetime.now(timezone.utc)
            await self.db.flush()

    async def mark_read(self, message_id: int, user_id: int):
        result = await self.db.execute(
            select(MessageStatus).where(
                MessageStatus.message_id == message_id,
                MessageStatus.user_id == user_id,
            )
        )
        ms = result.scalar_one_or_none()
        if ms and ms.status in ("sent", "delivered"):
            ms.status = "read"
            ms.status_at = datetime.now(timezone.utc)
            await self.db.flush()

    async def get_history(self, chat_id: int, user_id: int, before_sort_key: int | None = None, limit: int = 50) -> list[Message]:
        participant = await self.chats.get_participant(chat_id, user_id)
        if not participant:
            raise ForbiddenError("Not a participant")

        query = select(Message).where(
            Message.chat_id == chat_id,
            Message.is_deleted == False,
        )
        if before_sort_key:
            query = query.where(Message.sort_key < before_sort_key)
        query = query.order_by(Message.sort_key.desc()).limit(limit)

        result = await self.db.execute(query)
        return list(result.scalars().all())

    async def messages_to_dicts(self, messages: list[Message], viewer_id: int) -> list[dict]:
        if not messages:
            return []

        message_ids = [m.id for m in messages]
        status_result = await self.db.execute(
            select(MessageStatus).where(MessageStatus.message_id.in_(message_ids))
        )
        statuses_by_message: dict[int, list[MessageStatus]] = {}
        for status in status_result.scalars().all():
            statuses_by_message.setdefault(status.message_id, []).append(status)

        return [
            self.message_to_dict(m, self._message_status_for_viewer(m, viewer_id, statuses_by_message.get(m.id, [])))
            for m in messages
        ]

    def message_to_dict(self, msg: Message, status: str | None = None) -> dict:
        return {
            "id": msg.id,
            "client_id": msg.client_id,
            "chat_id": msg.chat_id,
            "sender_id": msg.sender_id,
            "type": msg.type,
            "content": msg.content,
            "album_id": msg.album_id,
            "reply_to_msg_id": msg.reply_to_msg_id,
            "edited_at": int(msg.edited_at.timestamp() * 1000) if msg.edited_at else None,
            "sort_key": msg.sort_key,
            "created_at": int(msg.created_at.timestamp() * 1000) if msg.created_at else None,
            "status": status,
        }

    def _message_status_for_viewer(
        self,
        msg: Message,
        viewer_id: int,
        statuses: list[MessageStatus],
    ) -> str:
        if msg.sender_id != viewer_id:
            own_status = next((s.status for s in statuses if s.user_id == viewer_id), None)
            return own_status or "sent"

        if not statuses:
            return "sent"

        min_priority = min(STATUS_PRIORITY.get(s.status, 0) for s in statuses)
        for name, priority in STATUS_PRIORITY.items():
            if priority == min_priority:
                return name
        return "sent"

    async def create_call_log(
        self,
        caller_id: int,
        callee_id: int,
        call_type: str,
        answered: bool,
        duration_ms: int | None = None,
        chat_id: int | None = None,
    ) -> Message | None:
        chat = None
        if chat_id is not None:
            chat = await self.chats.get_chat(chat_id, caller_id)
        else:
            chat = await self.chats.get_private_chat_between(caller_id, callee_id)

        if chat is None:
            return None

        if answered:
            kind = "Видеозвонок" if call_type == "video" else "Звонок"
            text = f"Исходящий {kind.lower()} - {_format_duration(duration_ms)}"
        else:
            text = f"Пропущенный {'видеозвонок' if call_type == 'video' else 'звонок'}"

        msg, _ = await self.send_message(
            sender_id=caller_id,
            chat_id=chat.id,
            content=text,
            msg_type="service",
        )
        return msg

    async def get_messages_since(self, user_id: int, since_sort_key: int, limit: int = 100) -> list[Message]:
        user_chats = await self.chats.get_user_chats(user_id)
        chat_ids = [c.id for c in user_chats]
        if not chat_ids:
            return []

        result = await self.db.execute(
            select(Message)
            .where(
                Message.chat_id.in_(chat_ids),
                Message.sort_key > since_sort_key,
                Message.is_deleted == False,
            )
            .order_by(Message.sort_key.asc())
            .limit(limit)
        )
        return list(result.scalars().all())

    async def _get_message(self, message_id: int) -> Message:
        result = await self.db.execute(
            select(Message).where(Message.id == message_id, Message.is_deleted == False)
        )
        msg = result.scalar_one_or_none()
        if not msg:
            raise NotFoundError("Message not found")
        return msg
