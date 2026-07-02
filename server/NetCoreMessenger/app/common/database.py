from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine, AsyncSession
from sqlalchemy.orm import DeclarativeBase

from app.common.config import settings
from sqlalchemy import inspect, text

engine = create_async_engine(settings.database_url, echo=settings.debug)
async_session_factory = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


async def get_db() -> AsyncSession:
    async with async_session_factory() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()


async def init_db():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
        await _ensure_message_columns(conn)
        await _ensure_media_columns(conn)


async def _ensure_message_columns(conn):
    def _sync_inspect(sync_conn):
        inspector = inspect(sync_conn)
        columns = {col["name"] for col in inspector.get_columns("messages")}
        if "album_id" not in columns:
            sync_conn.execute(text("ALTER TABLE messages ADD COLUMN album_id VARCHAR(36)"))

    await conn.run_sync(_sync_inspect)


async def _ensure_media_columns(conn):
    def _sync_inspect(sync_conn):
        inspector = inspect(sync_conn)
        columns = {col["name"] for col in inspector.get_columns("media")}
        additions = {
            "width": "ALTER TABLE media ADD COLUMN width INTEGER",
            "height": "ALTER TABLE media ADD COLUMN height INTEGER",
            "duration_ms": "ALTER TABLE media ADD COLUMN duration_ms INTEGER",
            "thumbnail_media_id": "ALTER TABLE media ADD COLUMN thumbnail_media_id BIGINT",
            "is_compressed": "ALTER TABLE media ADD COLUMN is_compressed BOOLEAN DEFAULT 0",
        }
        for name, ddl in additions.items():
            if name not in columns:
                sync_conn.execute(text(ddl))

    await conn.run_sync(_sync_inspect)
