"""
Публикация текущего адреса туннеля в Telegram-канал/чат через Bot API.

Схема:
  1. Отправляем сообщение с доменом (без https://).
  2. Сразу пиним его (unpin_all_chat_messages + pin_chat_message).

Андроид-клиент читает НЕ getUpdates (ненадёжно: 24ч, offset, webhook = 409),
а getChat.pinned_message.text — этот способ работает всегда.
"""
import logging
from typing import Optional

import requests

from app.common.config import settings

logger = logging.getLogger("netcore.telegram")

_API_BASE = "https://api.telegram.org"


def _api(method: str) -> str:
    return f"{_API_BASE}/bot{settings.telegram_bot_token}/{method}"


def _post(method: str, payload: dict) -> Optional[dict]:
    if not settings.telegram_bot_token or not settings.telegram_chat_id:
        logger.warning("Telegram не настроен (нет TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID) — пропускаю")
        return None
    try:
        resp = requests.post(_api(method), json=payload, timeout=10)
        if resp.status_code != 200:
            logger.error("Telegram %s -> %s: %s", method, resp.status_code, resp.text)
            return None
        data = resp.json()
        if not data.get("ok"):
            logger.error("Telegram %s not ok: %s", method, data)
            return None
        return data.get("result")
    except Exception:
        logger.exception("Telegram %s failed", method)
        return None


def send_message(text: str) -> Optional[dict]:
    return _post("sendMessage", {
        "chat_id": settings.telegram_chat_id,
        "text": text,
        "disable_web_page_preview": True,
        "disable_notification": True,
    })


def unpin_all() -> None:
    _post("unpinAllChatMessages", {"chat_id": settings.telegram_chat_id})


def pin_message(message_id: int) -> None:
    _post("pinChatMessage", {
        "chat_id": settings.telegram_chat_id,
        "message_id": message_id,
        "disable_notification": True,
    })


def notify_new_tunnel_domain(domain: str) -> None:
    """domain — просто хост, без схемы, например xxxx-xxxx-xxxx.trycloudflare.com.

    Клиенты читают его через getChat.pinned_message.text, поэтому
    ФОРМАТ СТРОГИЙ: последняя непустая строка сообщения — сам домен.
    """
    text = f"🌐 Актуальный адрес сервера:\n{domain}"
    result = send_message(text)
    if not result:
        return
    message_id = result.get("message_id")
    if not message_id:
        return
    # Снимаем прошлое закрепление и пинним новое — так андроид гарантированно
    # получит именно свежий домен через getChat.
    unpin_all()
    pin_message(int(message_id))
