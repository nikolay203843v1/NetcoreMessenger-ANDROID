import logging
import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI, WebSocket, Query
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware

from app.common.config import settings
from app.common.database import init_db
from app.common.middleware import RequestIDMiddleware
from app.common import models as common_models
from app.auth.router import router as auth_router
from app.users.router import router as users_router
from app.chats.router import router as chats_router
from app.messages.router import router as messages_router
from app.sync.router import router as sync_router
from app.media.router import router as media_router
from app.sync.queue import outbox_processor
from app.websocket.handler import on_connect
from app.websocket.presence import presence_tracker

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("netcore")


def _start_ngrok():
    """Открыть ngrok-туннель к локальному порту. Возвращает публичный URL или None."""
    if not settings.use_ngrok:
        return None
    try:
        from pyngrok import ngrok, conf
        if settings.ngrok_authtoken:
            conf.get_default().auth_token = settings.ngrok_authtoken
        kwargs = {"schemes": ["https"]}
        if settings.ngrok_domain:
            kwargs["domain"] = settings.ngrok_domain
        tunnel = ngrok.connect(settings.port, "http", **kwargs)
        public_url = tunnel.public_url
        logger.warning("==== NGROK ====")
        logger.warning("PUBLIC URL: %s", public_url)
        logger.warning("WS URL:     %s", public_url.replace("https://", "wss://"))
        logger.warning("===============")
        return public_url
    except Exception as e:
        logger.exception("Failed to start ngrok: %s", e)
        return None


def _start_cloudflared():
    """
    Открыть Cloudflare Quick Tunnel (без домена, без логина, без токена).
    URL при каждом рестарте новый — при успешном получении шлём его в Telegram.
    Возвращает публичный URL или None.
    """
    if not settings.use_cloudflared:
        return None
    try:
        from app.common.cloudflared import start_quick_tunnel
        from app.common.telegram_notify import notify_new_tunnel_domain

        public_url = start_quick_tunnel(settings.port, bin_path=settings.cloudflared_bin_path)
        if not public_url:
            logger.error("Не удалось получить URL от cloudflared за отведённое время")
            return None

        logger.warning("==== CLOUDFLARED ====")
        logger.warning("PUBLIC URL: %s", public_url)
        logger.warning("WS URL:     %s", public_url.replace("https://", "wss://"))
        logger.warning("======================")

        domain = public_url.replace("https://", "").replace("http://", "").rstrip("/")
        notify_new_tunnel_domain(domain)

        return public_url
    except Exception as e:
        logger.exception("Failed to start cloudflared: %s", e)
        return None


async def presence_checker():
    while True:
        await asyncio.sleep(30)
        timed_out = presence_tracker.check_timeouts()
        if timed_out:
            from app.websocket.manager import connection_registry
            from app.websocket.handler import _broadcast_presence_offline
            for uid in timed_out:
                logger.info("Presence timeout: user=%s", uid)
                connection_registry.remove_user(uid)
                await _broadcast_presence_offline(uid)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Starting NetCoreMessenger...")
    await init_db()
    logger.info("Database initialized")
    # _start_cloudflared внутри блокирующе ждёт до ~25с появления URL —
    # обязательно уносим в тред, иначе event loop встанет колом на старте.
    public_url = await asyncio.to_thread(_start_cloudflared)
    if not public_url:
        public_url = await asyncio.to_thread(_start_ngrok)
    if public_url:
        app.state.public_url = public_url
    bg_tasks = [
        asyncio.create_task(presence_checker()),
        asyncio.create_task(outbox_processor.run_forever()),
    ]
    yield
    for t in bg_tasks:
        t.cancel()
    try:
        from pyngrok import ngrok
        ngrok.kill()
    except Exception:
        pass
    try:
        from app.common.cloudflared import stop_quick_tunnel
        stop_quick_tunnel()
    except Exception:
        pass
    logger.info("Shutting down...")


app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
    lifespan=lifespan,
)

# allow_credentials=True вместе с allow_origins=["*"] запрещён CORS-спецификацией
# и небезопасен (позволяет CSRF при наличии credentials). Отключаем credentials,
# если origins не сужены — андроид-клиенту cookies не нужны, он шлёт Bearer в заголовке.
_wildcard_origin = settings.cors_origins == ["*"]
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=not _wildcard_origin,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-Request-ID"],
)
app.add_middleware(RequestIDMiddleware)

app.include_router(auth_router, prefix=settings.api_prefix)
app.include_router(users_router, prefix=settings.api_prefix)
app.include_router(chats_router, prefix=settings.api_prefix)
app.include_router(messages_router, prefix=settings.api_prefix)
app.include_router(sync_router, prefix=settings.api_prefix)
app.include_router(media_router, prefix=settings.api_prefix)


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.websocket("/ws/v1")
async def websocket_endpoint(
    websocket: WebSocket,
    token: str = Query(...),
    device_id: str = Query(...),
    platform: str | None = Query(None),
):
    await on_connect(websocket, token, device_id, platform)
