from dataclasses import dataclass, field
from typing import Any
from fastapi import WebSocket


@dataclass
class Connection:
    websocket: WebSocket
    user_id: int
    device_id: str
    platform: str | None = None


class ConnectionRegistry:
    def __init__(self):
        self._by_user: dict[int, dict[str, Connection]] = {}

    def add(self, conn: Connection):
        if conn.user_id not in self._by_user:
            self._by_user[conn.user_id] = {}
        self._by_user[conn.user_id][conn.device_id] = conn

    def remove(self, user_id: int, device_id: str):
        devices = self._by_user.get(user_id)
        if devices:
            devices.pop(device_id, None)
            if not devices:
                del self._by_user[user_id]

    def remove_user(self, user_id: int):
        self._by_user.pop(user_id, None)

    def get_user_connections(self, user_id: int) -> list[Connection]:
        devices = self._by_user.get(user_id, {})
        return list(devices.values())

    def is_online(self, user_id: int) -> bool:
        return user_id in self._by_user and bool(self._by_user[user_id])

    async def send_to_user(self, user_id: int, event: str, data: dict[str, Any], msg_id: str | None = None) -> int:
        delivered = 0
        for conn in self.get_user_connections(user_id):
            try:
                payload: dict[str, Any] = {"event": event, "data": data}
                if msg_id:
                    payload["id"] = msg_id
                await conn.websocket.send_json(payload)
                delivered += 1
            except Exception:
                pass
        return delivered

    async def broadcast_to_chat(self, chat_id: int, event: str, data: dict[str, Any], exclude_user_id: int | None = None, rooms_registry: Any = None):
        if rooms_registry is None:
            return
        members = rooms_registry.get_members(chat_id)
        for uid in members:
            if uid == exclude_user_id:
                continue
            await self.send_to_user(uid, event, data)

    @property
    def all_online_users(self) -> set[int]:
        return set(self._by_user.keys())


connection_registry = ConnectionRegistry()
