import time
import secrets


def generate_ulid() -> str:
    timestamp = int(time.time() * 1000)
    random_part = secrets.token_hex(8)
    return f"{timestamp:016x}{random_part}"


def generate_sort_key(sequence: int = 0) -> int:
    now_ms = int(time.time() * 1000)
    return now_ms * 1000 + (sequence % 1000)


def now_timestamp_ms() -> int:
    return int(time.time() * 1000)
