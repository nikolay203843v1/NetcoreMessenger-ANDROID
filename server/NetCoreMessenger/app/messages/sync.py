from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.messages.models import Message
from app.messages.service import MessageService
from app.sync.models import SyncState
from app.chats.service import ChatService


class SyncService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.msg_svc = MessageService(db)
        self.chats = ChatService(db)

    async def get_sync_state(self, user_id: int, device_id: int) -> SyncState:
        result = await self.db.execute(
            select(SyncState).where(
                SyncState.user_id == user_id,
                SyncState.device_id == device_id,
            )
        )
        state = result.scalar_one_or_none()
        if not state:
            state = SyncState(user_id=user_id, device_id=device_id)
            self.db.add(state)
            await self.db.flush()
        return state

    async def sync_messages(self, user_id: int, device_id: int, limit: int = 100) -> dict:
        state = await self.get_sync_state(user_id, device_id)
        messages = await self.msg_svc.get_messages_since(user_id, state.last_sort_key, limit)
        new_last_key = state.last_sort_key
        if messages:
            new_last_key = max(m.sort_key for m in messages)
            state.last_sort_key = new_last_key
            state.last_sync_at = __import__("datetime").datetime.now(__import__("datetime").timezone.utc)
            await self.db.flush()

        return {
            "messages": [self._msg_to_dict(m) for m in messages],
            "new_last_sort_key": new_last_key,
            "has_more": len(messages) >= limit,
        }

    def _msg_to_dict(self, msg: Message) -> dict:
        return {
            "id": msg.id,
            "client_id": msg.client_id,
            "chat_id": msg.chat_id,
            "sender_id": msg.sender_id,
            "type": msg.type,
            "content": msg.content,
            "reply_to_msg_id": msg.reply_to_msg_id,
            "sort_key": msg.sort_key,
            "created_at": int(msg.created_at.timestamp() * 1000) if msg.created_at else None,
        }
