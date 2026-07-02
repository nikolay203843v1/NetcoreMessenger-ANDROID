import json
from datetime import datetime, timezone
from sqlalchemy.ext.asyncio import AsyncSession

from app.websocket.manager import connection_registry
from app.websocket.rooms import room_registry
from app.messages.models import Message
from app.sync.models import OutboxEntry


async def _send_message_push(db: AsyncSession, recipient_id: int, msg: Message) -> None:
    try:
        from app.push.service import send_push_to_user
        from app.users.models import User
        from sqlalchemy import select

        sender_result = await db.execute(select(User).where(User.id == msg.sender_id))
        sender = sender_result.scalar_one_or_none()
        sender_name = sender.display_name if sender else "Netcore"
        avatar_media_id = sender.avatar_media_id if sender else None
        text = {
            "image": "Фото",
            "voice": "Голосовое сообщение",
            "video": "Видео",
            "circle": "Видеокружок",
        }.get(msg.type, msg.content or "")
        await send_push_to_user(
            db,
            recipient_id,
            sender_name,
            text,
            {
                "type": "message",
                "chat_id": msg.chat_id,
                "message_id": msg.id,
                "sender_id": msg.sender_id,
                "sender_name": sender_name,
                "sender_avatar_media_id": avatar_media_id,
                "message_kind": msg.type,
                "content": text,
            },
        )
    except Exception:
        import logging
        logging.getLogger("netcore.push").exception("Failed to send message push")


async def broadcast_new_message(db: AsyncSession, msg: Message):
    participants = []
    from app.chats.service import ChatService
    chats = ChatService(db)
    participants = await chats.get_participants(msg.chat_id)

    data = {
        "id": msg.id,
        "client_id": msg.client_id,
        "chat_id": msg.chat_id,
        "sender_id": msg.sender_id,
        "type": msg.type,
        "content": msg.content,
        "album_id": msg.album_id,
        "reply_to_msg_id": msg.reply_to_msg_id,
        "sort_key": msg.sort_key,
        "created_at": int(msg.created_at.timestamp() * 1000) if msg.created_at else None,
    }

    # Send message.new to all online participants, and save to outbox for offline ones
    for uid in participants:
        if uid == msg.sender_id:
            continue
        delivered = 0
        if connection_registry.is_online(uid):
            delivered = await connection_registry.send_to_user(uid, "message.new", data)
        if delivered == 0:
            entry = OutboxEntry(
                user_id=uid,
                message_id=msg.id,
                event_type="message.new",
                payload=json.dumps(data),
            )
            db.add(entry)
        if msg.type != "service":
            await _send_message_push(db, uid, msg)

    sent_data = {
        "client_id": msg.client_id,
        "id": msg.id,
        "sort_key": msg.sort_key,
    }
    await connection_registry.send_to_user(msg.sender_id, "message.sent", sent_data)


async def broadcast_delivered(message_id: int, chat_id: int, user_id: int):
    await connection_registry.send_to_user(
        user_id,
        "message.delivered",
        {"message_id": message_id, "chat_id": chat_id, "user_id": user_id, "status_at": int(datetime.now(timezone.utc).timestamp() * 1000)},
    )


async def broadcast_read(message_id: int, chat_id: int, user_id: int):
    await connection_registry.send_to_user(
        user_id,
        "message.read",
        {"message_id": message_id, "chat_id": chat_id, "user_id": user_id, "status_at": int(datetime.now(timezone.utc).timestamp() * 1000)},
    )
