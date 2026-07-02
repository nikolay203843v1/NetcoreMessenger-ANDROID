import asyncio
import json
import logging
from typing import Any

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.models import DeviceSession
from app.common.config import settings

logger = logging.getLogger("netcore.push")

_credentials: service_account.Credentials | None = None
_project_id: str | None = None

FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
RESERVED_DATA_KEYS = {"from", "message_type", "collapse_key"}
RESERVED_DATA_PREFIXES = ("google.", "gcm.")


def _safe_data_key(key: str) -> str:
    if key in RESERVED_DATA_KEYS or key.startswith(RESERVED_DATA_PREFIXES):
        return f"custom_{key.replace('.', '_')}"
    return key


def _safe_data(data: dict[str, Any]) -> dict[str, str]:
    return {
        _safe_data_key(str(k)): "" if v is None else str(v)
        for k, v in data.items()
    }


def _load_credentials() -> service_account.Credentials:
    global _credentials, _project_id
    if _credentials is None:
        _credentials = service_account.Credentials.from_service_account_file(
            settings.firebase_credentials_path,
            scopes=[FCM_SCOPE],
        )
        _project_id = _credentials.project_id
    return _credentials


def _get_project_id() -> str:
    global _project_id
    if _project_id:
        return _project_id
    try:
        return _load_credentials().project_id
    except Exception:
        with open(settings.firebase_credentials_path) as f:
            data = json.load(f)
        _project_id = data["project_id"]
        return _project_id


def _get_access_token() -> str:
    credentials = _load_credentials()
    if not credentials.valid:
        credentials.refresh(Request())
    return credentials.token


def _send_fcm_sync(token: str, title: str, body: str, data: dict[str, Any]) -> tuple[str, int, str]:
    project_id = _get_project_id()
    access_token = _get_access_token()
    payload = {
        "message": {
            "token": token,
            "data": _safe_data(data),
            "android": {
                "priority": "HIGH",
            },
        }
    }
    if title or body:
        payload["message"]["data"]["title"] = title
        payload["message"]["data"]["body"] = body

    response = requests.post(
        f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send",
        headers={
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json; charset=utf-8",
        },
        json=payload,
        timeout=10,
    )
    if response.status_code >= 400:
        logger.warning("FCM send failed: status=%s body=%s", response.status_code, response.text[:500])
    return token, response.status_code, response.text


def _is_unregistered_fcm_response(response_text: str) -> bool:
    lowered = response_text.lower()
    return "unregistered" in lowered or ("not_found" in lowered and '"code": 404' in lowered)


async def _deactivate_push_token(db: AsyncSession, token: str) -> None:
    result = await db.execute(
        select(DeviceSession).where(
            DeviceSession.push_token == token,
            DeviceSession.is_active == True,
        )
    )
    rows = result.scalars().all()
    if not rows:
        return
    for session in rows:
        session.push_token = None
    await db.flush()


async def send_push_to_user(
    db: AsyncSession,
    user_id: int,
    title: str,
    body: str,
    data: dict[str, Any],
) -> None:
    result = await db.execute(
        select(DeviceSession.push_token).where(
            DeviceSession.user_id == user_id,
            DeviceSession.is_active == True,
            DeviceSession.push_token.is_not(None),
        )
    )
    tokens = [row[0] for row in result.all() if row[0]]
    if not tokens:
        return

    payload = dict(data)
    payload.setdefault("title", title)
    payload.setdefault("body", body)
    results = await asyncio.gather(
        *[
            asyncio.to_thread(_send_fcm_sync, token, title, body, payload)
            for token in tokens
        ],
        return_exceptions=True,
    )
    stale_tokens: list[str] = []
    for result in results:
        if isinstance(result, Exception):
            continue
        token, status, response_text = result
        if status in (404, 410) and _is_unregistered_fcm_response(response_text):
            stale_tokens.append(token)
    if stale_tokens:
        rows = await db.execute(
            select(DeviceSession).where(
                DeviceSession.push_token.in_(stale_tokens),
                DeviceSession.is_active == True,
            )
        )
        for session in rows.scalars().all():
            session.push_token = None
        await db.flush()
