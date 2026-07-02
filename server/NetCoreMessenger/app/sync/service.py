from datetime import datetime, timezone
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.sync.models import SyncState
from app.messages.service import MessageService
from app.chats.service import ChatService


class SyncService:
    def __init__(self, db: AsyncSession):
        self.db = db
        self.msg_svc = MessageService(db)
        self.chats = ChatService(db)

    async def get_sync_state(self, user_id: int, device_session_id: int) -> SyncState:
        result = await self.db.execute(
            select(SyncState).where(
                SyncState.user_id == user_id,
                SyncState.device_id == device_session_id,
            )
        )
        state = result.scalar_one_or_none()
        if not state:
            state = SyncState(user_id=user_id, device_id=device_session_id)
            self.db.add(state)
            await self.db.flush()
        return state

    async def sync_messages(self, user_id: int, device_session_id: int, limit: int = 100) -> dict:
        state = await self.get_sync_state(user_id, device_session_id)
        messages = await self.msg_svc.get_messages_since(user_id, state.last_sort_key, limit)

        new_last_key = state.last_sort_key
        if messages:
            new_last_key = max(m.sort_key for m in messages)
            state.last_sort_key = new_last_key
            state.last_sync_at = datetime.now(timezone.utc)
            await self.db.flush()

        return {
            "messages": await self.msg_svc.messages_to_dicts(messages, user_id),
            "new_last_sort_key": new_last_key,
            "has_more": len(messages) >= limit,
        }
