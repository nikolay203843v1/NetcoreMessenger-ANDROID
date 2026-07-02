"""
Точка входа для Pterodactyl-панели (она запускает: python run.py).
Импортирует ASGI-приложение из app.main и поднимает uvicorn.
"""
import uvicorn

from app.common.config import settings


if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host=settings.host,
        port=settings.port,
        proxy_headers=True,
        forwarded_allow_ips="*",
        log_level="info",
    )
