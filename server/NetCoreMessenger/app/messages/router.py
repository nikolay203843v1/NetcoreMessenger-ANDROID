from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.database import get_db
from app.auth.dependencies import get_current_user
from app.messages.service import MessageService
from app.messages.hooks import broadcast_new_message

router = APIRouter(prefix="/messages", tags=["messages"])


class MessageOut(BaseModel):
    id: int
    client_id: str
    chat_id: int
    sender_id: int
    type: str
    content: str
    album_id: str | None = None
    reply_to_msg_id: int | None = None
    edited_at: int | None = None
    sort_key: int
    created_at: int | None = None
    status: str | None = None

    model_config = {"from_attributes": True}


class SendMessageRequest(BaseModel):
    chat_id: int
    type: str = "text"
    content: str
    client_id: str | None = None
    album_id: str | None = None
    reply_to_msg_id: int | None = None


class EditMessageRequest(BaseModel):
    content: str


@router.post("", response_model=MessageOut, status_code=201)
async def send_message(
    body: SendMessageRequest,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = MessageService(db)
    msg, is_new = await service.send_message(
        sender_id=current_user["user_id"],
        chat_id=body.chat_id,
        content=body.content,
        msg_type=body.type,
        client_id=body.client_id,
        album_id=body.album_id,
        reply_to_msg_id=body.reply_to_msg_id,
    )
    if is_new:
        await broadcast_new_message(db, msg)
    return service.message_to_dict(msg, "sent")


@router.get("/{chat_id}", response_model=list[MessageOut])
async def get_history(
    chat_id: int,
    before_sort_key: int | None = Query(None),
    limit: int = Query(50, ge=1, le=200),
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = MessageService(db)
    messages = await service.get_history(chat_id, current_user["user_id"], before_sort_key, limit)
    return await service.messages_to_dicts(messages, current_user["user_id"])


@router.patch("/{message_id}", response_model=MessageOut)
async def edit_message(
    message_id: int,
    body: EditMessageRequest,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = MessageService(db)
    msg = await service.edit_message(message_id, current_user["user_id"], body.content)
    return service.message_to_dict(msg)


@router.delete("/{message_id}", status_code=200)
async def delete_message(
    message_id: int,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = MessageService(db)
    await service.delete_message(message_id, current_user["user_id"])
    return {"status": "ok"}


@router.post("/{message_id}/forward", response_model=MessageOut, status_code=201)
async def forward_message(
    message_id: int,
    body: SendMessageRequest,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = MessageService(db)
    msg, is_new = await service.forward_message(message_id, current_user["user_id"], body.chat_id)
    if is_new:
        await broadcast_new_message(db, msg)
    return service.message_to_dict(msg, "sent")
