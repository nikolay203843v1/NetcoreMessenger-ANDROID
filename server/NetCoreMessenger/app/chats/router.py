from datetime import datetime
from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field, field_serializer
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.database import get_db
from app.auth.dependencies import get_current_user
from app.chats.service import ChatService
from app.users.router import UserOut

router = APIRouter(prefix="/chats", tags=["chats"])

class ParticipantOut(BaseModel):
    user_id: int
    role: str
    joined_at: datetime
    user: UserOut | None = None

    model_config = {"from_attributes": True}

    @field_serializer("joined_at")
    def serialize_joined_at(self, dt: datetime) -> int:
        return int(dt.timestamp() * 1000) if dt else 0


class ChatOut(BaseModel):
    id: int
    type: str
    title: str | None = None
    description: str | None = None
    avatar_media_id: int | None = None
    creator_id: int
    invite_link: str | None = None
    created_at: datetime
    updated_at: datetime
    participants: list[ParticipantOut] | None = None

    model_config = {"from_attributes": True}

    @field_serializer("created_at", "updated_at")
    def serialize_dt(self, dt: datetime) -> int:
        return int(dt.timestamp() * 1000) if dt else 0


class CreateChatRequest(BaseModel):
    type: str = Field(..., pattern=r"^(private|group|channel)$")
    title: str | None = None
    participant_ids: list[int] = Field(default_factory=list)


class JoinChatRequest(BaseModel):
    invite_link: str | None = None


class UpdateChatRequest(BaseModel):
    title: str | None = Field(None, max_length=128)
    description: str | None = None
    avatar_media_id: int | None = None


@router.post("", response_model=ChatOut, status_code=201)
async def create_chat(
    body: CreateChatRequest,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = ChatService(db)
    chat = await service.create_chat(
        creator_id=current_user["user_id"],
        chat_type=body.type,
        title=body.title,
        participant_ids=body.participant_ids,
    )
    
    # Broadcast chat.new event to other participants
    from app.websocket.manager import connection_registry
    p_ids = []
    for p in (chat.participants or []):
        if isinstance(p, dict):
            p_ids.append(p["user_id"])
        else:
            p_ids.append(p.user_id)
            
    event_data = {
        "id": chat.id,
        "type": chat.type,
        "participants": p_ids,
        "created_at": int(chat.created_at.timestamp() * 1000) if chat.created_at else 0
    }
    for uid in p_ids:
        if uid != current_user["user_id"]:
            await connection_registry.send_to_user(uid, "chat.new", event_data)
            
    return chat


@router.get("", response_model=list[ChatOut])
async def list_chats(
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = ChatService(db)
    return await service.get_user_chats(current_user["user_id"])


@router.get("/{chat_id}", response_model=ChatOut)
async def get_chat(
    chat_id: int,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = ChatService(db)
    chat = await service.get_chat(chat_id, current_user["user_id"])
    return chat


@router.post("/{chat_id}/join", response_model=ChatOut)
async def join_chat(
    chat_id: int,
    body: JoinChatRequest,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = ChatService(db)
    await service.join_chat(chat_id, current_user["user_id"], body.invite_link)
    return await service.get_chat(chat_id)


@router.patch("/{chat_id}", response_model=ChatOut)
async def update_chat(
    chat_id: int,
    body: UpdateChatRequest,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = ChatService(db)
    chat = await service.update_chat(
        chat_id=chat_id,
        actor_id=current_user["user_id"],
        title=body.title,
        description=body.description,
        avatar_media_id=body.avatar_media_id,
    )
    from app.websocket.manager import connection_registry
    participants = await service.get_participants(chat_id)
    event_data = {
        "id": chat.id,
        "title": chat.title,
        "description": chat.description,
        "avatar_media_id": chat.avatar_media_id,
    }
    for uid in participants:
        await connection_registry.send_to_user(uid, "chat.updated", event_data)
    return chat


@router.post("/{chat_id}/participants/{user_id}", response_model=ChatOut)
async def add_participant(
    chat_id: int,
    user_id: int,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = ChatService(db)
    await service.add_participant(chat_id, current_user["user_id"], user_id)
    chat = await service.get_chat(chat_id)
    from app.websocket.manager import connection_registry
    participants = await service.get_participants(chat_id)
    event_data = {
        "id": chat.id,
        "type": chat.type,
        "participants": participants,
        "created_at": int(chat.created_at.timestamp() * 1000) if chat.created_at else 0,
    }
    for uid in participants:
        await connection_registry.send_to_user(uid, "chat.new", event_data)
    return chat


@router.delete("/{chat_id}/participants/{user_id}", response_model=ChatOut)
async def remove_participant(
    chat_id: int,
    user_id: int,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = ChatService(db)
    await service.remove_participant(chat_id, current_user["user_id"], user_id)
    return await service.get_chat(chat_id)


@router.post("/{chat_id}/leave", status_code=200)
async def leave_chat(
    chat_id: int,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = ChatService(db)
    await service.leave_chat(chat_id, current_user["user_id"])
    return {"status": "ok"}
