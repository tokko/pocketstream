import aiosqlite
from pathlib import Path

from .config import settings

_SCHEMA = """
CREATE TABLE IF NOT EXISTS episodes (
    id TEXT PRIMARY KEY,
    video_id TEXT UNIQUE NOT NULL,
    url TEXT NOT NULL,
    title TEXT,
    duration INTEGER,
    thumbnail_url TEXT,
    filesize INTEGER,
    status TEXT NOT NULL DEFAULT 'queued',
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    downloaded_at DATETIME
);
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT);
"""


async def get_db() -> aiosqlite.Connection:
    db = await aiosqlite.connect(str(settings.db_path))
    db.row_factory = aiosqlite.Row
    await db.execute("PRAGMA journal_mode=WAL")
    return db


async def init_db() -> None:
    settings.data_dir.mkdir(parents=True, exist_ok=True)
    settings.audio_dir.mkdir(parents=True, exist_ok=True)

    db = await get_db()
    try:
        await db.executescript(_SCHEMA)
        await db.commit()
    finally:
        await db.close()


async def get_queue_depth(conn: aiosqlite.Connection) -> int:
    cur = await conn.execute(
        "SELECT COUNT(*) FROM episodes WHERE status IN ('queued', 'downloading')"
    )
    row = await cur.fetchone()
    return row[0]


async def insert_episode(conn: aiosqlite.Connection, **kwargs) -> None:
    cols = ", ".join(kwargs.keys())
    placeholders = ", ".join("?" for _ in kwargs)
    await conn.execute(
        f"INSERT OR IGNORE INTO episodes ({cols}) VALUES ({placeholders})",
        list(kwargs.values()),
    )
    await conn.commit()


async def get_episode_by_video_id(conn: aiosqlite.Connection, video_id: str) -> dict | None:
    cur = await conn.execute("SELECT * FROM episodes WHERE video_id = ?", (video_id,))
    row = await cur.fetchone()
    return dict(row) if row else None


async def get_episode_by_id(conn: aiosqlite.Connection, episode_id: str) -> dict | None:
    cur = await conn.execute("SELECT * FROM episodes WHERE id = ?", (episode_id,))
    row = await cur.fetchone()
    return dict(row) if row else None


async def get_downloaded_episodes(conn: aiosqlite.Connection) -> list[dict]:
    cur = await conn.execute(
        "SELECT * FROM episodes WHERE status = 'downloaded' ORDER BY downloaded_at DESC"
    )
    rows = await cur.fetchall()
    return [dict(r) for r in rows]


async def get_meta(conn: aiosqlite.Connection, key: str) -> str | None:
    cur = await conn.execute("SELECT value FROM meta WHERE key = ?", (key,))
    row = await cur.fetchone()
    return row[0] if row else None


async def set_meta(conn: aiosqlite.Connection, key: str, value: str) -> None:
    await conn.execute(
        "INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)", (key, value)
    )
    await conn.commit()


async def update_episode_status(
    conn: aiosqlite.Connection,
    episode_id: str,
    status: str,
    **kwargs,
) -> None:
    parts = ["status = ?"]
    vals = [status]
    for k, v in kwargs.items():
        parts.append(f"{k} = ?")
        vals.append(v)
    vals.append(episode_id)
    await conn.execute(
        f"UPDATE episodes SET {', '.join(parts)} WHERE id = ?", vals
    )
    await conn.commit()
