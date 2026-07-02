import re

USERNAME_PATTERN = re.compile(r"^[a-zA-Z0-9_]{5,32}$")


def validate_username(username: str) -> str | None:
    if not USERNAME_PATTERN.match(username):
        return "Username must be 5-32 characters, alphanumeric + underscore"
    return None
