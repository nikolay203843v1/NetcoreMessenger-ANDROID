import json
import logging
from datetime import datetime, timezone
from fastapi import WebSocket, WebSocketDisconnect
from sqlalchemy import select, update

from app.websocket.manager import connection_registry, Connection
from app.websocket.rooms import room_registry
from app.websocket.presence import presence_tracker
from app.websocket.dispatcher import dispatch_event
from app.auth.service import decode_token
from app.common.errors import UnauthorizedError

logger = logging.getLogger("netcore.ws")


async def _get_user_contacts(user_id: int) -> set[int]:
    """Все пользователи, с которыми у нас есть общие чаты."""
    from app.common.database import async_session_factory
    from app.chats.models import ChatParticipant

    async with async_session_factory() as db:
        r = await db.execute(
            select(ChatParticipant.chat_id).where(
                ChatParticipant.user_id == user_id,
                ChatParticipant.left_at.is_(None),
            )
        )
        chat_ids = [row[0] for row in r.all()]
        if not chat_ids:
            return set()

        r = await db.execute(
            select(ChatParticipant.user_id).where(
                ChatParticipant.chat_id.in_(chat_ids),
                ChatParticipant.left_at.is_(None),
            ).distinct()
        )
        contacts = {row[0] for row in r.all()}
        contacts.discard(user_id)
        return contacts


async def _broadcast_presence_online(user_id: int):
    """Сообщить всем chat-mates, что юзер онлайн + отдать ему текущие статусы контактов."""
    try:
        from app.common.database import async_session_factory
        from app.users.models import User

        presence_tracker.set_online(user_id)
        async with async_session_factory() as db:
            await db.execute(update(User).where(User.id == user_id).values(status="online"))
            await db.commit()

        contacts = await _get_user_contacts(user_id)
        for uid in contacts:
            await connection_registry.send_to_user(
                uid, "presence.online", {"user_id": user_id}
            )
        # Отдадим нашему клиенту, какие из его контактов сейчас в сети
        for uid in contacts:
            if connection_registry.is_online(uid):
                await connection_registry.send_to_user(
                    user_id, "presence.online", {"user_id": uid}
                )
    except Exception as e:
        logger.exception("broadcast_presence_online failed: %s", e)


async def _broadcast_presence_offline(user_id: int):
    try:
        from app.common.database import async_session_factory
        from app.users.models import User

        last_seen = datetime.now(timezone.utc)
        presence_tracker.set_offline(user_id)
        async with async_session_factory() as db:
            await db.execute(
                update(User).where(User.id == user_id).values(
                    status="offline", last_online_at=last_seen
                )
            )
            await db.commit()

        contacts = await _get_user_contacts(user_id)
        for uid in contacts:
            await connection_registry.send_to_user(
                uid,
                "presence.offline",
                {
                    "user_id": user_id,
                    "last_seen": int(last_seen.timestamp() * 1000),
                },
            )
    except Exception as e:
        logger.exception("broadcast_presence_offline failed: %s", e)


async def on_connect(websocket: WebSocket, token: str, device_id: str, platform: str | None = None):
    try:
        payload = decode_token(token)
    except Exception:
        await websocket.close(code=4001)
        return

    user_id = int(payload["sub"])
    if payload.get("type") != "access":
        await websocket.close(code=4001)
        return

    # Сверяем device_id из query с привязанным в access-токене,
    # иначе клиент сможет имитировать любую сессию того же юзера.
    token_device_id = payload.get("device_id")
    if token_device_id and token_device_id != device_id:
        await websocket.close(code=4001)
        return
    device_id = token_device_id or device_id

    await websocket.accept()

    conn = Connection(websocket=websocket, user_id=user_id, device_id=device_id, platform=platform)
    connection_registry.add(conn)
    presence_tracker.heartbeat(user_id)

    logger.info("WS connected: user=%s device=%s", user_id, device_id)
    try:
        await websocket.send_json({"event": "connected", "data": {"connection_id": device_id}})
    except WebSocketDisconnect:
        connection_registry.remove(user_id, device_id)
        logger.info("WS disconnected during handshake: user=%s device=%s", user_id, device_id)
        return
    # Сразу подписываем в комнаты всех чатов, где состоит юзер
    try:
        from app.common.database import async_session_factory
        from app.chats.models import ChatParticipant
        async with async_session_factory() as db:
            r = await db.execute(
                select(ChatParticipant.chat_id).where(
                    ChatParticipant.user_id == user_id,
                    ChatParticipant.left_at.is_(None),
                )
            )
            for row in r.all():
                room_registry.join(row[0], user_id)
    except Exception as e:
        logger.exception("room join failed: %s", e)

    # На connect не ставим online: Android может держать WS в фоне ради доставки.
    # Online означает foreground-приложение и приходит отдельным presence.foreground.
    try:
        contacts = await _get_user_contacts(user_id)
        for uid in contacts:
            if presence_tracker.is_online(uid):
                await connection_registry.send_to_user(
                    user_id, "presence.online", {"user_id": uid}
                )
    except Exception as e:
        logger.exception("presence snapshot failed: %s", e)

    try:
        while True:
            raw = await websocket.receive_text()
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                await websocket.send_json({"event": "error", "data": {"code": "INVALID_JSON", "message": "Invalid JSON"}})
                continue

            event = msg.get("event", "")
            data = msg.get("data", {})
            msg_id = msg.get("id")

            await dispatch_event(user_id, device_id, event, data, msg_id)
    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.exception("WS error: user=%s device=%s", user_id, device_id)
    finally:
        connection_registry.remove(user_id, device_id)
        if not connection_registry.is_online(user_id):
            # Сообщаем всем контактам — мы оффлайн (и пишем БД)
            await _broadcast_presence_offline(user_id)
        logger.info("WS disconnected: user=%s device=%s", user_id, device_id)
