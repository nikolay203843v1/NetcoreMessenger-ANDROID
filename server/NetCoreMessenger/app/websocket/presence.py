import time
from dataclasses import dataclass, field


@dataclass
class PresenceInfo:
    user_id: int
    last_heartbeat: float = 0.0
    is_online: bool = False


class PresenceTracker:
    HEARTBEAT_TIMEOUT = 90

    def __init__(self):
        self._users: dict[int, PresenceInfo] = {}

    def heartbeat(self, user_id: int):
        now = time.time()
        info = self._users.get(user_id)
        if info:
            info.last_heartbeat = now
        else:
            self._users[user_id] = PresenceInfo(user_id=user_id, last_heartbeat=now, is_online=False)

    def set_online(self, user_id: int):
        now = time.time()
        info = self._users.get(user_id)
        if info:
            info.last_heartbeat = now
            info.is_online = True
        else:
            self._users[user_id] = PresenceInfo(user_id=user_id, last_heartbeat=now, is_online=True)

    def set_offline(self, user_id: int):
        info = self._users.get(user_id)
        if info:
            info.is_online = False

    def is_online(self, user_id: int) -> bool:
        info = self._users.get(user_id)
        if not info:
            return False
        if not info.is_online:
            return False
        if time.time() - info.last_heartbeat > self.HEARTBEAT_TIMEOUT:
            info.is_online = False
            return False
        return True

    def remove(self, user_id: int):
        self._users.pop(user_id, None)

    def check_timeouts(self) -> list[int]:
        now = time.time()
        timed_out = []
        for uid, info in list(self._users.items()):
            if info.is_online and now - info.last_heartbeat > self.HEARTBEAT_TIMEOUT:
                info.is_online = False
                timed_out.append(uid)
        return timed_out


presence_tracker = PresenceTracker()
