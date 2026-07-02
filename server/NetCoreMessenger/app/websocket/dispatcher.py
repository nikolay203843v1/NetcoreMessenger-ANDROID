import json
import logging
from datetime import datetime, timezone
from typing import Any

from app.websocket.events import EventType
from app.websocket.manager import connection_registry
from app.websocket.rooms import room_registry
from app.websocket.presence import presence_tracker

logger = logging.getLogger("netcore.ws")


def _as_utc(dt: datetime) -> datetime:
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


async def dispatch_event(user_id: int, device_id: str, event: str, data: dict[str, Any], msg_id: str | None = None):
    handler = HANDLERS.get(event)
    if handler is None:
        logger.warning("Unknown event: %s from user=%s", event, user_id)
        await connection_registry.send_to_user(user_id, EventType.ERROR, {"code": "UNKNOWN_EVENT", "message": f"Unknown event: {event}"}, msg_id)
        return

    try:
        await handler(user_id, device_id, data, msg_id)
    except Exception as e:
        logger.exception("Handler error for event=%s user=%s", event, user_id)
        await connection_registry.send_to_user(user_id, EventType.ERROR, {"code": "INTERNAL_ERROR", "message": str(e)}, msg_id)


async def handle_ping(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    presence_tracker.heartbeat(user_id)
    await connection_registry.send_to_user(user_id, EventType.PONG, {}, msg_id)


async def handle_presence_foreground(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    from app.websocket.handler import _broadcast_presence_online

    await _broadcast_presence_online(user_id)


async def handle_presence_background(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    from app.websocket.handler import _broadcast_presence_offline

    await _broadcast_presence_offline(user_id)


async def handle_typing_start(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    chat_id = data.get("chat_id")
    if not chat_id:
        return
    await connection_registry.broadcast_to_chat(chat_id, EventType.TYPING_START, {"chat_id": chat_id, "user_id": user_id}, exclude_user_id=user_id, rooms_registry=room_registry)


async def handle_typing_stop(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    chat_id = data.get("chat_id")
    if not chat_id:
        return
    await connection_registry.broadcast_to_chat(chat_id, EventType.TYPING_STOP, {"chat_id": chat_id, "user_id": user_id}, exclude_user_id=user_id, rooms_registry=room_registry)


async def handle_message_send(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    from app.common.database import async_session_factory
    from app.messages.service import MessageService
    from app.messages.hooks import broadcast_new_message

    async with async_session_factory() as db:
        service = MessageService(db)
        try:
            msg, is_new = await service.send_message(
                sender_id=user_id,
                chat_id=data["chat_id"],
                content=data.get("content", ""),
                msg_type=data.get("type", "text"),
                client_id=data.get("client_id"),
                album_id=data.get("album_id"),
                reply_to_msg_id=data.get("reply_to_msg_id"),
            )
            if is_new:
                await broadcast_new_message(db, msg)
            await db.commit()
        except Exception as e:
            await db.rollback()
            await connection_registry.send_to_user(user_id, EventType.ERROR, {"code": "SEND_FAILED", "message": str(e)}, msg_id)
            return

    if msg_id:
        await connection_registry.send_to_user(user_id, EventType.ACK, {"ack_id": msg_id, "status": "ok"}, None)


async def handle_message_delivered(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    from app.common.database import async_session_factory
    from app.messages.service import MessageService

    async with async_session_factory() as db:
        service = MessageService(db)
        await service.mark_delivered(data["message_id"], user_id)
        await db.commit()

    msg_data = data.get("message_id")
    chat_id = data.get("chat_id")
    if msg_data and chat_id:
        await connection_registry.broadcast_to_chat(
            chat_id, EventType.MESSAGE_DELIVERED,
            {"message_id": data["message_id"], "chat_id": chat_id, "user_id": user_id},
            exclude_user_id=user_id, rooms_registry=room_registry
        )


async def handle_message_read(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    from app.common.database import async_session_factory
    from app.messages.service import MessageService

    async with async_session_factory() as db:
        service = MessageService(db)
        await service.mark_read(data["message_id"], user_id)
        await db.commit()

    chat_id = data.get("chat_id")
    if chat_id:
        await connection_registry.broadcast_to_chat(
            chat_id, EventType.MESSAGE_READ,
            {"message_id": data["message_id"], "chat_id": chat_id, "user_id": user_id},
            exclude_user_id=user_id, rooms_registry=room_registry
        )


async def handle_message_react(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    chat_id = data.get("chat_id")
    if chat_id:
        await connection_registry.broadcast_to_chat(
            chat_id, EventType.MESSAGE_REACT,
            {"message_id": data["message_id"], "user_id": user_id, "emoji": data.get("emoji")},
            exclude_user_id=user_id, rooms_registry=room_registry,
        )


async def handle_chat_new(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    from app.common.database import async_session_factory
    from app.chats.service import ChatService

    async with async_session_factory() as db:
        service = ChatService(db)
        chat = await service.create_chat(
            creator_id=user_id,
            chat_type=data.get("type", "private"),
            title=data.get("title"),
            participant_ids=data.get("participant_ids"),
        )
        await db.commit()

        participants = await service.get_participants(chat.id)
        for uid in participants:
            room_registry.join(chat.id, uid)

        chat_data = {
            "id": chat.id,
            "type": chat.type,
            "title": chat.title,
            "participants": participants,
            "created_at": int(chat.created_at.timestamp() * 1000) if chat.created_at else None,
        }
        for uid in participants:
            await connection_registry.send_to_user(uid, EventType.CHAT_NEW, chat_data)

    if msg_id:
        await connection_registry.send_to_user(user_id, EventType.ACK, {"ack_id": msg_id, "status": "ok"}, None)


# ===== Звонки (WebRTC сигналинг) =====

async def _forward_to_peer(actor_id: int, target_user_id: int, event_type: str, data: dict[str, Any]):
    """Прокинуть сигнал другому участнику звонка."""
    payload = dict(data)
    payload["from_user_id"] = actor_id
    return await connection_registry.send_to_user(target_user_id, event_type, payload)


async def _send_call_push(
    recipient_id: int,
    caller_id: int,
    call_id: int,
    call_type: str,
    chat_id: int | None,
):
    try:
        from app.common.database import async_session_factory
        from app.push.service import send_push_to_user
        from app.users.models import User
        from sqlalchemy import select as _s

        async with async_session_factory() as db:
            r = await db.execute(_s(User).where(User.id == caller_id))
            caller = r.scalar_one_or_none()
            caller_name = caller.display_name if caller else "Netcore"
            await send_push_to_user(
                db,
                recipient_id,
                "Входящий видеозвонок" if call_type == "video" else "Входящий звонок",
                caller_name,
                {
                    "type": "incoming_call",
                    "call_id": call_id,
                    "caller_id": caller_id,
                    "caller_name": caller_name,
                    "caller_avatar_media_id": caller.avatar_media_id if caller else None,
                    "call_type": call_type,
                    "chat_id": chat_id,
                },
            )
    except Exception:
        logger.exception("Failed to send incoming call push")


async def _send_call_cancel_push(recipient_id: int, call_id: int):
    try:
        from app.common.database import async_session_factory
        from app.push.service import send_push_to_user

        async with async_session_factory() as db:
            await send_push_to_user(
                db,
                recipient_id,
                "",
                "",
                {
                    "type": "call_cancelled",
                    "call_id": call_id,
                },
            )
    except Exception:
        logger.exception("Failed to send call cancel push")


async def handle_call_start(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    """
    Инициатор начинает звонок. data: { call_id, callee_id, chat_id?, call_type ('audio'|'video') }
    """
    callee_id = int(data.get("callee_id") or 0)
    if not callee_id:
        return
    from app.common.database import async_session_factory
    from app.webrtc.models import CallSession
    async with async_session_factory() as db:
        call = CallSession(
            caller_id=user_id, callee_id=callee_id,
            chat_id=data.get("chat_id"),
            call_type=data.get("call_type", "audio"),
            state="ringing",
        )
        db.add(call)
        await db.commit()
        await db.refresh(call)
    # Уведомляем callee
    delivered = await _forward_to_peer(user_id, callee_id, EventType.CALL_RINGING, {
        "call_id": call.id,
        "caller_id": user_id,
        "call_type": call.call_type,
        "chat_id": call.chat_id,
    })
    await _send_call_push(callee_id, user_id, call.id, call.call_type, call.chat_id)
    # Подтверждение caller-у: запомни id
    await connection_registry.send_to_user(user_id, EventType.CALL_START, {
        "call_id": call.id,
        "callee_id": callee_id,
        "call_type": call.call_type,
    }, msg_id)


async def handle_call_accept(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    call_id = int(data.get("call_id") or 0)
    if not call_id:
        return
    from app.common.database import async_session_factory
    from sqlalchemy import select as _s
    from app.webrtc.models import CallSession
    async with async_session_factory() as db:
        r = await db.execute(_s(CallSession).where(CallSession.id == call_id))
        call = r.scalar_one_or_none()
        if not call:
            return
        if call.state not in ("ringing", "calling"):
            await connection_registry.send_to_user(user_id, EventType.CALL_ENDED, {"call_id": call_id})
            return
        call.state = "active"
        await db.commit()
        caller_id = call.caller_id
    await _forward_to_peer(user_id, caller_id, EventType.CALL_ACCEPT, {"call_id": call_id})


async def handle_call_reject(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    call_id = int(data.get("call_id") or 0)
    if not call_id:
        return
    from app.common.database import async_session_factory
    from sqlalchemy import select as _s
    from app.webrtc.models import CallSession
    from app.messages.service import MessageService
    from app.messages.hooks import broadcast_new_message
    async with async_session_factory() as db:
        r = await db.execute(_s(CallSession).where(CallSession.id == call_id))
        call = r.scalar_one_or_none()
        if not call:
            return
        if call.state in ("rejected", "ended"):
            return
        call.state = "rejected"
        call.ended_at = datetime.now(timezone.utc)
        caller_id = call.caller_id
        callee_id = call.callee_id
        call_type = call.call_type
        chat_id = call.chat_id
        await db.commit()
        log_service = MessageService(db)
        log_msg = await log_service.create_call_log(
            caller_id=caller_id,
            callee_id=callee_id,
            call_type=call_type,
            answered=False,
            duration_ms=None,
            chat_id=chat_id,
        )
        if log_msg:
            await broadcast_new_message(db, log_msg)
            await db.commit()
    await _forward_to_peer(user_id, caller_id, EventType.CALL_REJECT, {"call_id": call_id})


async def handle_call_end(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    call_id = int(data.get("call_id") or 0)
    if not call_id:
        return
    from app.common.database import async_session_factory
    from sqlalchemy import select as _s, or_
    from app.webrtc.models import CallSession
    from app.messages.service import MessageService
    from app.messages.hooks import broadcast_new_message
    async with async_session_factory() as db:
        r = await db.execute(_s(CallSession).where(CallSession.id == call_id))
        call = r.scalar_one_or_none()
        if not call:
            # Fallback: if caller ends call before receiving the real call_id,
            # find their current ringing or active call and end it.
            r = await db.execute(
                _s(CallSession).where(
                    or_(CallSession.caller_id == user_id, CallSession.callee_id == user_id),
                    CallSession.state.in_(["ringing", "active"])
                ).order_by(CallSession.id.desc()).limit(1)
            )
            call = r.scalar_one_or_none()
            if not call:
                return
        if call.state == "ended":
            return
        was_active = call.state == "active"
        call.state = "ended"
        call.ended_at = datetime.now(timezone.utc)
        if call.started_at:
            call.duration_ms = int((_as_utc(call.ended_at) - _as_utc(call.started_at)).total_seconds() * 1000)
        await db.commit()
        other = call.callee_id if user_id == call.caller_id else call.caller_id
        log_service = MessageService(db)
        log_msg = await log_service.create_call_log(
            caller_id=call.caller_id,
            callee_id=call.callee_id,
            call_type=call.call_type,
            answered=was_active,
            duration_ms=call.duration_ms if was_active else None,
            chat_id=call.chat_id,
        )
        if log_msg:
            await broadcast_new_message(db, log_msg)
            await db.commit()
    delivered = await _forward_to_peer(user_id, other, EventType.CALL_ENDED, {"call_id": call.id})
    await _send_call_cancel_push(other, call.id)


async def handle_sync_request(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    from app.common.database import async_session_factory
    from app.sync.service import SyncService
    from app.auth.models import DeviceSession
    from sqlalchemy import select as _s

    async with async_session_factory() as db:
        stmt = _s(DeviceSession).where(
            DeviceSession.user_id == user_id,
            DeviceSession.device_id == device_id,
            DeviceSession.is_active == True
        )
        r = await db.execute(stmt)
        session = r.scalar_one_or_none()
        if not session:
            session_id = hash(device_id) % 100000000
        else:
            session_id = session.id

        service = SyncService(db)
        sync_result = await service.sync_messages(user_id, session_id)
        await db.commit()

    await connection_registry.send_to_user(
        user_id,
        EventType.SYNC_MESSAGES,
        sync_result,
        msg_id
    )


HANDLERS: dict[str, Any] = {
    EventType.PING: handle_ping,
    EventType.PRESENCE_FOREGROUND: handle_presence_foreground,
    EventType.PRESENCE_BACKGROUND: handle_presence_background,
    EventType.TYPING_START: handle_typing_start,
    EventType.TYPING_STOP: handle_typing_stop,
    EventType.MESSAGE_SEND: handle_message_send,
    EventType.MESSAGE_DELIVERED: handle_message_delivered,
    EventType.MESSAGE_READ: handle_message_read,
    EventType.MESSAGE_REACT: handle_message_react,
    EventType.CHAT_NEW: handle_chat_new,
    EventType.CALL_START: handle_call_start,
    EventType.CALL_ACCEPT: handle_call_accept,
    EventType.CALL_REJECT: handle_call_reject,
    EventType.CALL_END: handle_call_end,
    EventType.SYNC_REQUEST: handle_sync_request,
}

# WebRTC SDP/ICE: чистый прокси сигналинг к получателю
async def _h_webrtc_offer(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    target = int(data.get("to_user_id") or 0)
    if target:
        await _forward_to_peer(user_id, target, EventType.WEBRTC_OFFER, data)


async def _h_webrtc_answer(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    target = int(data.get("to_user_id") or 0)
    if target:
        await _forward_to_peer(user_id, target, EventType.WEBRTC_ANSWER, data)


async def _h_webrtc_ice(user_id: int, device_id: str, data: dict[str, Any], msg_id: str | None = None):
    target = int(data.get("to_user_id") or 0)
    if target:
        await _forward_to_peer(user_id, target, EventType.WEBRTC_ICE, data)


HANDLERS[EventType.WEBRTC_OFFER] = _h_webrtc_offer
HANDLERS[EventType.WEBRTC_ANSWER] = _h_webrtc_answer
HANDLERS[EventType.WEBRTC_ICE] = _h_webrtc_ice
