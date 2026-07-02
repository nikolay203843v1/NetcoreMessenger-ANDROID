import json
import logging
import asyncio
from datetime import datetime, timezone, timedelta
from sqlalchemy import select, update

from app.common.database import async_session_factory
from app.sync.models import OutboxEntry
from app.websocket.manager import connection_registry

logger = logging.getLogger("netcore.outbox")


class OutboxProcessor:
    BATCH_SIZE = 50
    POLL_INTERVAL = 1.0

    async def process_once(self) -> int:
        processed = 0
        async with async_session_factory() as db:
            now = datetime.now(timezone.utc)
            result = await db.execute(
                select(OutboxEntry)
                .where(
                    OutboxEntry.is_delivered == False,
                    OutboxEntry.next_retry_at <= now,
                )
                .order_by(OutboxEntry.next_retry_at.asc())
                .limit(self.BATCH_SIZE)
            )
            entries = list(result.scalars().all())

            for entry in entries:
                delivered = False
                try:
                    user_online = connection_registry.is_online(entry.user_id)
                    if user_online:
                        payload = json.loads(entry.payload)
                        await connection_registry.send_to_user(
                            entry.user_id, entry.event_type, payload
                        )
                        delivered = True
                except Exception as e:
                    logger.warning("Outbox send failed entry=%s: %s", entry.id, e)

                if delivered:
                    entry.is_delivered = True
                else:
                    entry.retry_count += 1
                    if entry.retry_count >= entry.max_retries:
                        entry.is_delivered = True
                    else:
                        delay = min(60, 2 ** (entry.retry_count - 1))
                        entry.next_retry_at = datetime.now(timezone.utc) + timedelta(seconds=delay)

                processed += 1

            if entries:
                await db.commit()

        return processed

    async def run_forever(self):
        logger.info("Outbox processor started")
        while True:
            try:
                count = await self.process_once()
                if count:
                    logger.debug("Outbox: processed %d entries", count)
            except Exception as e:
                logger.exception("Outbox processor error: %s", e)
            await asyncio.sleep(self.POLL_INTERVAL)


outbox_processor = OutboxProcessor()
