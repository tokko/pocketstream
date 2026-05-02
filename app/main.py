import asyncio
import logging
import multiprocessing
import os

from fastapi import FastAPI

from .config import settings
from .db import init_db, get_db
from .routes.enqueue import router as enqueue_router
from .routes.feed import router as feed_router
from .routes.audio import router as audio_router
from .routes.health import router as health_router

log = logging.getLogger("pocketstream")

app = FastAPI(title="PocketStream", version="1.0.0")

app.include_router(enqueue_router)
app.include_router(feed_router)
app.include_router(audio_router)
app.include_router(health_router)

_worker_process: multiprocessing.Process | None = None
_prune_task: asyncio.Task | None = None


@app.on_event("startup")
async def startup():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
    )

    await init_db()
    log.info("Database initialized at %s", settings.db_path)

    # Start download worker
    from .worker import worker_main
    global _worker_process
    _worker_process = multiprocessing.Process(target=worker_main, daemon=True)
    _worker_process.start()
    log.info("Worker started (pid %d)", _worker_process.pid)

    # Start pruning background task
    global _prune_task
    _prune_task = asyncio.create_task(_prune_loop())


@app.on_event("shutdown")
async def shutdown():
    global _worker_process, _prune_task
    if _prune_task:
        _prune_task.cancel()
    if _worker_process and _worker_process.is_alive():
        _worker_process.terminate()
        _worker_process.join(timeout=10)
        log.info("Worker stopped")


async def _prune_loop():
    """Background task: prune old audio files every 6h if over storage limit."""
    while True:
        try:
            await asyncio.sleep(6 * 3600)  # 6 hours
            await _prune_audio()
        except asyncio.CancelledError:
            return
        except Exception:
            log.exception("Prune error")


async def _prune_audio():
    """Delete oldest audio files until under storage limit."""
    max_bytes = settings.max_storage_gb * 1024 ** 3
    audio_dir = settings.audio_dir

    if not audio_dir.exists():
        return

    # Sum current size
    total = 0
    files = []
    for f in audio_dir.iterdir():
        if f.is_file() and f.suffix == ".m4a":
            sz = f.stat().st_size
            total += sz
            files.append((f, sz))

    if total <= max_bytes:
        log.info("Prune check: %0.1f GB / %d GB — ok", total / 1024**3, settings.max_storage_gb)
        return

    log.warning("Prune: %0.1f GB over %d GB limit, cleaning oldest", total / 1024**3, settings.max_storage_gb)

    # Sort by modification time, oldest first
    files.sort(key=lambda x: x[0].stat().st_mtime)

    db = await get_db()
    try:
        for f, sz in files:
            if total <= max_bytes:
                break
            ep_id = f.stem
            f.unlink(missing_ok=True)
            total -= sz
            log.info("Pruned %s (%0.1f MB)", f.name, sz / 1024**2)

            # Also clean info json if present
            info_file = audio_dir / f"{ep_id}.info.json"
            info_file.unlink(missing_ok=True)

            await db.execute(
                "UPDATE episodes SET status = 'pruned', filesize = NULL WHERE id = ?",
                (ep_id,),
            )
            await db.commit()

        # Regenerate feed after pruning
        from .rss import regenerate_feed
        await regenerate_feed()
    finally:
        await db.close()
