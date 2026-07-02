from pydantic_settings import BaseSettings
from typing import Optional, List


class Settings(BaseSettings):
    app_name: str = "NetCoreMessenger"
    debug: bool = False
    api_prefix: str = "/api/v1"

    database_url: str = "sqlite+aiosqlite:///./netcore.db"
    redis_url: Optional[str] = None

    # Обязательный секрет. Читается из .env. Дефолта нет — если не задан, приложение упадёт при импорте.
    secret_key: str
    jwt_algorithm: str = "RS256"
    jwt_access_token_ttl_minutes: int = 15
    jwt_refresh_token_ttl_days: int = 30

    rsa_private_key_path: str = "./private_key.pem"
    rsa_public_key_path: str = "./public_key.pem"

    firebase_credentials_path: str = "./firebase-service-account.json"
    firebase_token_clock_skew_seconds: int = 30

    # ---- Networking / runtime ----
    host: str = "0.0.0.0"
    port: int = 8000
    cors_origins: List[str] = ["*"]  # сузить под прод

    # ---- ngrok ----
    use_ngrok: bool = False
    ngrok_authtoken: Optional[str] = None
    ngrok_domain: Optional[str] = None  # reserved domain если есть платный план

    # ---- Cloudflare Quick Tunnel (без домена, без логина) ----
    use_cloudflared: bool = False
    cloudflared_bin_path: str = "./cloudflared"  # куда качать/где искать бинарник

    # ---- Telegram-уведомление о новом адресе туннеля ----
    telegram_bot_token: Optional[str] = None
    telegram_chat_id: Optional[str] = None  # id канала/чата, куда слать ссылку

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()
