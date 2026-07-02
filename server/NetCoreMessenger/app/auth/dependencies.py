from fastapi import Depends, Header
from sqlalchemy.ext.asyncio import AsyncSession
from jose import JWTError

from app.common.database import get_db
from app.common.errors import UnauthorizedError
from app.auth.service import decode_token


async def get_current_user(
    authorization: str | None = Header(None),
    db: AsyncSession = Depends(get_db),
) -> dict:
    if not authorization or not authorization.startswith("Bearer "):
        raise UnauthorizedError("Invalid authorization header")
    token = authorization.removeprefix("Bearer ")
    try:
        payload = decode_token(token)
    except (JWTError, ValueError):
        raise UnauthorizedError("Invalid or expired token")

    if payload.get("type") != "access":
        raise UnauthorizedError("Invalid token type")

    return {
        "user_id": int(payload["sub"]),
        "device_id": payload.get("device_id"),
    }


async def get_optional_user(
    authorization: str | None = Header(None),
    db: AsyncSession = Depends(get_db),
) -> dict | None:
    if not authorization or not authorization.startswith("Bearer "):
        return None
    token = authorization.removeprefix("Bearer ")
    try:
        payload = decode_token(token)
    except (JWTError, ValueError):
        return None
    return {
        "user_id": int(payload["sub"]),
        "device_id": payload.get("device_id"),
    }
