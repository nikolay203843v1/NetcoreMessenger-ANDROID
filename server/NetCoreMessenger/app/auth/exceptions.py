from app.common.errors import AppException


class InvalidToken(AppException):
    def __init__(self):
        super().__init__(code="AUTH_INVALID", message="Invalid authentication token")
