class RoomRegistry:
    def __init__(self):
        self._rooms: dict[int, set[int]] = {}

    def join(self, chat_id: int, user_id: int):
        if chat_id not in self._rooms:
            self._rooms[chat_id] = set()
        self._rooms[chat_id].add(user_id)

    def leave(self, chat_id: int, user_id: int):
        members = self._rooms.get(chat_id)
        if members:
            members.discard(user_id)
            if not members:
                del self._rooms[chat_id]

    def get_members(self, chat_id: int) -> set[int]:
        return self._rooms.get(chat_id, set())

    def is_member(self, chat_id: int, user_id: int) -> bool:
        return user_id in self._rooms.get(chat_id, set())


room_registry = RoomRegistry()
