from fastapi import HTTPException, status


class AppException(HTTPException):
    def __init__(self, code: str, message: str, status_code: int = status.HTTP_400_BAD_REQUEST):
        self.code = code
        self.message = message
        super().__init__(status_code=status_code, detail={"code": code, "message": message})


class NotFoundError(AppException):
    def __init__(self, message: str = "Resource not found"):
        super().__init__(code="NOT_FOUND", message=message, status_code=status.HTTP_404_NOT_FOUND)


class ForbiddenError(AppException):
    def __init__(self, message: str = "Forbidden"):
        super().__init__(code="FORBIDDEN", message=message, status_code=status.HTTP_403_FORBIDDEN)


class UnauthorizedError(AppException):
    def __init__(self, message: str = "Unauthorized"):
        super().__init__(code="AUTH_INVALID", message=message, status_code=status.HTTP_401_UNAUTHORIZED)


class RateLimitedError(AppException):
    def __init__(self, message: str = "Too many requests"):
        super().__init__(code="RATE_LIMITED", message=message, status_code=status.HTTP_429_TOO_MANY_REQUESTS)
