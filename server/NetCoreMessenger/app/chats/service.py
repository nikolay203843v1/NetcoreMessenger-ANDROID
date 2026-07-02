import secrets
from datetime import datetime, timezone
from sqlalchemy import select, or_, and_
from sqlalchemy.ext.asyncio import AsyncSession

from app.chats.models import Chat, ChatParticipant, ChatPending
from app.chats.permissions import is_admin
from app.common.errors import NotFoundError, ForbiddenError


class ChatService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def create_chat(self, creator_id: int, chat_type: str, title: str | None = None, participant_ids: list[int] | None = None) -> Chat:
        if chat_type == "private":
            if not participant_ids or len(participant_ids) != 1:
                raise ValueError("Private chat requires exactly one other participant")
            other_id = participant_ids[0]
            existing = await self._find_private_chat(creator_id, other_id)
            if existing:
                return existing

        chat = Chat(
            type=chat_type,
            title=title,
            creator_id=creator_id,
            invite_link=secrets.token_urlsafe(16) if chat_type == "group" else None,
        )
        self.db.add(chat)
        await self.db.flush()

        all_participants = [creator_id] + (participant_ids or [])
        for uid in set(all_participants):
            role = "creator" if uid == creator_id else "member"
            cp = ChatParticipant(chat_id=chat.id, user_id=uid, role=role)
            self.db.add(cp)

        await self.db.flush()
        chat.participants = await self.get_participants_with_users(chat.id)
        return chat

    async def _find_private_chat(self, user_a: int, user_b: int) -> Chat | None:
        subq = (
            select(ChatParticipant.chat_id)
            .where(ChatParticipant.user_id.in_([user_a, user_b]), ChatParticipant.left_at.is_(None))
            .group_by(ChatParticipant.chat_id)
            .having(ChatParticipant.chat_id == ChatParticipant.chat_id)
        )
        result = await self.db.execute(
            select(Chat)
            .where(Chat.id.in_(subq), Chat.type == "private", Chat.is_deleted == False)
        )
        chats = result.scalars().all()
        for c in chats:
            members = await self.get_participants(c.id)
            if sorted(members) == sorted([user_a, user_b]):
                c.participants = await self.get_participants_with_users(c.id)
                return c
        return None

    async def get_private_chat_between(self, user_a: int, user_b: int) -> Chat | None:
        return await self._find_private_chat(user_a, user_b)

    async def get_chat(self, chat_id: int, user_id: int | None = None) -> Chat:
        result = await self.db.execute(
            select(Chat).where(Chat.id == chat_id, Chat.is_deleted == False)
        )
        chat = result.scalar_one_or_none()
        if not chat:
            raise NotFoundError("Chat not found")
        chat.participants = await self.get_participants_with_users(chat.id)
        return chat

    async def get_user_chats(self, user_id: int) -> list[Chat]:
        result = await self.db.execute(
            select(Chat)
            .join(ChatParticipant, ChatParticipant.chat_id == Chat.id)
            .where(
                ChatParticipant.user_id == user_id,
                ChatParticipant.left_at.is_(None),
                Chat.is_deleted == False,
            )
            .order_by(Chat.updated_at.desc())
        )
        chats = list(result.scalars().all())
        for chat in chats:
            chat.participants = await self.get_participants_with_users(chat.id)
        return chats

    async def get_participants_with_users(self, chat_id: int) -> list[dict]:
        from app.users.models import User
        result = await self.db.execute(
            select(ChatParticipant, User)
            .join(User, User.id == ChatParticipant.user_id)
            .where(
                ChatParticipant.chat_id == chat_id,
                ChatParticipant.left_at.is_(None),
                User.is_deleted == False
            )
        )
        participants_data = []
        for cp, user in result.all():
            participants_data.append({
                "user_id": cp.user_id,
                "role": cp.role,
                "joined_at": cp.joined_at,
                "user": user
            })
        return participants_data

    async def get_participants(self, chat_id: int) -> list[int]:
        result = await self.db.execute(
            select(ChatParticipant.user_id)
            .where(ChatParticipant.chat_id == chat_id, ChatParticipant.left_at.is_(None))
        )
        return [row[0] for row in result.all()]

    async def get_participant(self, chat_id: int, user_id: int) -> ChatParticipant | None:
        result = await self.db.execute(
            select(ChatParticipant).where(
                ChatParticipant.chat_id == chat_id,
                ChatParticipant.user_id == user_id,
                ChatParticipant.left_at.is_(None),
            )
        )
        return result.scalar_one_or_none()

    async def join_chat(self, chat_id: int, user_id: int, invite_link: str | None = None) -> ChatParticipant:
        chat = await self.get_chat(chat_id)
        if chat.type == "private":
            raise ForbiddenError("Cannot join private chat")

        existing = await self.get_participant(chat_id, user_id)
        if existing:
            return existing

        if chat.invite_link and invite_link != chat.invite_link:
            raise ForbiddenError("Invalid invite link")

        cp = ChatParticipant(chat_id=chat_id, user_id=user_id, role="member")
        self.db.add(cp)
        await self.db.flush()
        return cp

    async def leave_chat(self, chat_id: int, user_id: int):
        cp = await self.get_participant(chat_id, user_id)
        if not cp:
            raise NotFoundError("Not a participant")
        cp.left_at = datetime.now(timezone.utc)
        await self.db.flush()

    async def update_chat(
        self,
        chat_id: int,
        actor_id: int,
        title: str | None = None,
        description: str | None = None,
        avatar_media_id: int | None = None,
    ) -> Chat:
        chat = await self.get_chat(chat_id)
        actor = await self.get_participant(chat_id, actor_id)
        if not actor or not is_admin(actor):
            raise ForbiddenError("Only admins can edit chat")
        if chat.type == "private":
            raise ForbiddenError("Cannot edit private chat")
        if title is not None:
            chat.title = title
        if description is not None:
            chat.description = description
        if avatar_media_id is not None:
            chat.avatar_media_id = avatar_media_id
        await self.db.flush()
        chat.participants = await self.get_participants_with_users(chat.id)
        return chat

    async def add_participant(self, chat_id: int, actor_id: int, target_id: int):
        actor = await self.get_participant(chat_id, actor_id)
        if not actor or not is_admin(actor):
            raise ForbiddenError("Only admins can add participants")

        existing = await self.get_participant(chat_id, target_id)
        if existing:
            return existing

        cp = ChatParticipant(chat_id=chat_id, user_id=target_id, role="member")
        self.db.add(cp)
        await self.db.flush()
        return cp

    async def remove_participant(self, chat_id: int, actor_id: int, target_id: int):
        actor = await self.get_participant(chat_id, actor_id)
        if not actor or not is_admin(actor):
            raise ForbiddenError("Only admins can remove participants")
        target = await self.get_participant(chat_id, target_id)
        if not target:
            raise NotFoundError("Participant not found")
        target.left_at = datetime.now(timezone.utc)
        await self.db.flush()
