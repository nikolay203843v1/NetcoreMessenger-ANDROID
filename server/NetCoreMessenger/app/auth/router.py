from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.database import get_db
from app.auth.dependencies import get_current_user
from app.auth.service import AuthService

router = APIRouter(prefix="/auth", tags=["auth"])


class GoogleLoginRequest(BaseModel):
    id_token: str = Field(..., min_length=1)
    device_id: str = Field(..., min_length=1, max_length=64)
    device_name: str | None = None
    platform: str | None = None
    push_token: str | None = None


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str
    expires_in: int
    user_id: int | None = None


class RefreshRequest(BaseModel):
    refresh_token: str


class PushTokenRequest(BaseModel):
    push_token: str | None = None


@router.post("/google", response_model=TokenResponse)
async def google_login(
    body: GoogleLoginRequest,
    db: AsyncSession = Depends(get_db),
):
    service = AuthService(db)
    return await service.google_login(
        id_token=body.id_token,
        device_id=body.device_id,
        device_name=body.device_name,
        platform=body.platform,
        push_token=body.push_token,
    )


@router.post("/refresh", response_model=TokenResponse)
async def refresh(
    body: RefreshRequest,
    db: AsyncSession = Depends(get_db),
):
    service = AuthService(db)
    return await service.refresh(body.refresh_token)


@router.post("/push-token")
async def update_push_token(
    body: PushTokenRequest,
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = AuthService(db)
    await service.update_push_token(
        user_id=current_user["user_id"],
        device_id=current_user["device_id"],
        push_token=body.push_token,
    )
    return {"ok": True}
