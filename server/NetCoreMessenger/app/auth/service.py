import json
import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

from google.oauth2 import id_token as google_id_token
from google.auth.transport import requests as google_requests
from jose import jwt
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.config import settings
from app.auth.models import DeviceSession
from app.auth.exceptions import InvalidToken

logger = logging.getLogger("netcore.auth")


_rsa_private_key: str | None = None
_rsa_public_key: str | None = None
_google_request: google_requests.Request | None = None
_firebase_project_id: str | None = None


def _get_google_request() -> google_requests.Request:
    global _google_request
    if _google_request is None:
        _google_request = google_requests.Request()
    return _google_request


def _get_firebase_project_id() -> str:
    """Берём project_id из service-account JSON (без firebase-admin)."""
    global _firebase_project_id
    if _firebase_project_id is None:
        with open(settings.firebase_credentials_path) as f:
            data = json.load(f)
        _firebase_project_id = data.get("project_id")
        if not _firebase_project_id:
            raise RuntimeError("project_id missing in firebase credentials json")
    return _firebase_project_id


def verify_firebase_id_token(token: str) -> dict:
    """
    Проверяет подпись Firebase ID-токена через публичные ключи Google,
    сверяет audience с project_id. Возвращает payload.
    """
    project_id = _get_firebase_project_id()
    logger.info("Verifying Firebase ID token (project_id=%s, token_len=%s)", project_id, len(token) if token else 0)
    try:
        decoded = google_id_token.verify_firebase_token(
            token,
            _get_google_request(),
            audience=project_id,
            clock_skew_in_seconds=settings.firebase_token_clock_skew_seconds,
        )
    except Exception as e:
        logger.warning("Firebase token verify failed: %s: %s", type(e).__name__, e)
        raise InvalidToken()
    if not decoded:
        logger.warning("Firebase token verify returned empty payload")
        raise InvalidToken()
    logger.info(
        "Firebase token OK: uid=%s aud=%s iss=%s email=%s",
        decoded.get("sub") or decoded.get("user_id"),
        decoded.get("aud"),
        decoded.get("iss"),
        decoded.get("email"),
    )
    return decoded


def _load_rsa_keys():
    global _rsa_private_key, _rsa_public_key
    if _rsa_private_key is None:
        with open(settings.rsa_private_key_path) as f:
            _rsa_private_key = f.read()
    if _rsa_public_key is None:
        try:
            with open(settings.rsa_public_key_path) as f:
                _rsa_public_key = f.read()
        except FileNotFoundError:
            _rsa_public_key = None
    return _rsa_private_key, _rsa_public_key


def create_access_token(user_id: int, device_id: str) -> str:
    private_key, _ = _load_rsa_keys()
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "device_id": device_id,
        "iat": now,
        "exp": now + timedelta(minutes=settings.jwt_access_token_ttl_minutes),
        "type": "access",
    }
    return jwt.encode(payload, private_key, algorithm=settings.jwt_algorithm)


def create_refresh_token(user_id: int, device_id: str) -> str:
    private_key, _ = _load_rsa_keys()
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "device_id": device_id,
        "iat": now,
        "exp": now + timedelta(days=settings.jwt_refresh_token_ttl_days),
        "type": "refresh",
    }
    return jwt.encode(payload, private_key, algorithm=settings.jwt_algorithm)


def decode_token(token: str) -> dict:
    _, public_key = _load_rsa_keys()
    if public_key is None:
        raise ValueError("Public key not found")
    payload = jwt.decode(token, public_key, algorithms=[settings.jwt_algorithm])
    return payload


class AuthService:
    def __init__(self, db: AsyncSession):
        self.db = db

    async def google_login(self, id_token: str, device_id: str, device_name: Optional[str] = None, platform: Optional[str] = None, push_token: Optional[str] = None) -> dict:
        try:
            decoded = verify_firebase_id_token(id_token)
        except InvalidToken:
            raise
        except Exception as e:
            logger.exception("Unexpected error verifying Firebase token: %s", e)
            raise InvalidToken()

        # В Firebase ID-token uid лежит в sub
        google_id = decoded.get("user_id") or decoded.get("sub") or decoded.get("uid")
        if not google_id:
            raise InvalidToken()
        email = decoded.get("email") or ""
        name = decoded.get("name") or (email.split("@")[0] if email else google_id[:8])

        from app.users.models import User
        user_result = await self.db.execute(
            select(User).where(User.google_id == google_id)
        )
        user = user_result.scalar_one_or_none()

        if user is None:
            user = User(google_id=google_id, display_name=name[:64])
            self.db.add(user)
            await self.db.flush()

        access_token = create_access_token(user.id, device_id)
        refresh_token = create_refresh_token(user.id, device_id)

        session_result = await self.db.execute(
            select(DeviceSession).where(DeviceSession.device_id == device_id)
        )
        session = session_result.scalar_one_or_none()
        if session is None:
            session = DeviceSession(device_id=device_id)
            self.db.add(session)

        session.user_id = user.id
        session.device_name = device_name
        session.platform = platform
        session.push_token = push_token
        session.is_active = True
        session.refresh_token = refresh_token
        session.refresh_token_expires_at = datetime.now(timezone.utc) + timedelta(days=settings.jwt_refresh_token_ttl_days)
        session.last_active_at = datetime.now(timezone.utc)
        await self.db.flush()

        return {
            "access_token": access_token,
            "refresh_token": refresh_token,
            "token_type": "bearer",
            "expires_in": settings.jwt_access_token_ttl_minutes * 60,
            "user_id": user.id,
        }

    async def refresh(self, refresh_token: str) -> dict:
        try:
            payload = decode_token(refresh_token)
        except Exception:
            raise InvalidToken()

        if payload.get("type") != "refresh":
            raise InvalidToken()

        user_id = int(payload["sub"])
        device_id = payload["device_id"]

        session_result = await self.db.execute(
            select(DeviceSession).where(
                DeviceSession.device_id == device_id,
                DeviceSession.user_id == user_id,
                DeviceSession.is_active == True,
                DeviceSession.refresh_token == refresh_token,
            )
        )
        session = session_result.scalar_one_or_none()
        if not session:
            raise InvalidToken()

        new_access = create_access_token(user_id, device_id)
        new_refresh = create_refresh_token(user_id, device_id)

        session.refresh_token = new_refresh
        session.refresh_token_expires_at = datetime.now(timezone.utc) + timedelta(days=settings.jwt_refresh_token_ttl_days)
        await self.db.flush()

        return {
            "access_token": new_access,
            "refresh_token": new_refresh,
            "token_type": "bearer",
            "expires_in": settings.jwt_access_token_ttl_minutes * 60,
        }

    async def update_push_token(self, user_id: int, device_id: str, push_token: str | None) -> None:
        result = await self.db.execute(
            select(DeviceSession).where(
                DeviceSession.user_id == user_id,
                DeviceSession.device_id == device_id,
                DeviceSession.is_active == True,
            )
        )
        session = result.scalar_one_or_none()
        if not session:
            return
        session.push_token = push_token
        session.last_active_at = datetime.now(timezone.utc)
        await self.db.flush()
