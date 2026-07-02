from enum import Enum
from dataclasses import dataclass, field
from typing import Any


class EventType(str, Enum):
    CONNECTED = "connected"
    ACK = "ack"
    ERROR = "error"

    MESSAGE_SEND = "message.send"
    MESSAGE_NEW = "message.new"
    MESSAGE_SENT = "message.sent"
    MESSAGE_DELIVERED = "message.delivered"
    MESSAGE_READ = "message.read"
    MESSAGE_REACT = "message.react"

    TYPING_START = "typing.start"
    TYPING_STOP = "typing.stop"

    PRESENCE_ONLINE = "presence.online"
    PRESENCE_OFFLINE = "presence.offline"
    PRESENCE_FOREGROUND = "presence.foreground"
    PRESENCE_BACKGROUND = "presence.background"

    CHAT_NEW = "chat.new"
    CHAT_UPDATED = "chat.updated"

    CALL_START = "call.start"
    CALL_RINGING = "call.ringing"
    CALL_ACCEPT = "call.accept"
    CALL_REJECT = "call.reject"
    CALL_END = "call.end"
    CALL_ENDED = "call.ended"

    WEBRTC_OFFER = "webrtc.offer"
    WEBRTC_ANSWER = "webrtc.answer"
    WEBRTC_ICE = "webrtc.ice"

    PING = "ping"
    PONG = "pong"

    SYNC_REQUEST = "sync.request"
    SYNC_MESSAGES = "sync.messages"


@dataclass
class Event:
    event: str
    data: dict[str, Any] = field(default_factory=dict)
    id: str | None = None
    timestamp: int | None = None
