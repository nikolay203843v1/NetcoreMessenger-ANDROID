from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from app.common.database import get_db
from app.auth.dependencies import get_current_user
from app.sync.service import SyncService

router = APIRouter(prefix="/sync", tags=["sync"])


class SyncResponse(BaseModel):
    messages: list[dict]
    new_last_sort_key: int
    has_more: bool


@router.post("/messages", response_model=SyncResponse)
async def sync_messages(
    device_session_id: int = Query(..., description="Device session ID"),
    limit: int = Query(100, ge=1, le=1000),
    current_user: dict = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    service = SyncService(db)
    return await service.sync_messages(
        user_id=current_user["user_id"],
        device_session_id=device_session_id,
        limit=limit,
    )
