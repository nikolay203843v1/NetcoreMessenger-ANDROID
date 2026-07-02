from typing import Generic, TypeVar, Optional, Sequence
from pydantic import BaseModel

T = TypeVar("T")


class CursorPage(BaseModel, Generic[T]):
    items: Sequence[T]
    next_cursor: Optional[str] = None
    has_more: bool = False


def decode_cursor(cursor: str) -> int:
    import base64
    try:
        return int(base64.urlsafe_b64decode(cursor))
    except Exception:
        return 0


def encode_cursor(value: int) -> str:
    import base64
    return base64.urlsafe_b64encode(str(value).encode()).decode()
