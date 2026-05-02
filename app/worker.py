"""
Download worker — runs as a separate multiprocessing.Process.
Uses synchronous sqlite3 (separate process, no async).
"""
import json
import logging
import os
import sqlite3
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

from .config import settings

log = logging.getLogger("pocketstream.worker")

_DB_PATH = str(settings.db_path)
_AUDIO_DIR = str(settings.audio_dir)
_DATA_DIR = str(settings.data_dir)
_BASE_URL = settings.base_url


def _get_sync_db() -> sqlite3.Connection:
    conn = sqlite3.connect(_DB_PATH)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.row_factory = sqlite3.Row
    return conn


def _startup_recovery(conn: sqlite3.Connection) -> None:
    """Reset stale downloading states and clean partial files."""
    cur = conn.execute(
        "UPDATE episodes SET status = 'queued' WHERE status = 'downloading'"
    )
    if cur.rowcount:
        log.info("Recovered %d stale downloads", cur.rowcount)
    conn.commit()

    # Clean partial files from interrupted downloads
    audio_dir = Path(_AUDIO_DIR)
    if audio_dir.exists():
        for f in audio_dir.iterdir():
            if f.suffix in (".part", ".temp", ".ytdl"):
                f.unlink(missing_ok=True)
                log.info("Cleaned partial file: %s", f.name)


def _heartbeat(conn: sqlite3.Connection) -> None:
    conn.execute(
        "INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)",
        ("worker_heartbeat", datetime.now(timezone.utc).isoformat()),
    )
    conn.commit()


def _claim_next(conn: sqlite3.Connection) -> dict | None:
    """Atomically claim the next queued episode."""
    row = conn.execute(
        "SELECT * FROM episodes WHERE status = 'queued' ORDER BY created_at ASC LIMIT 1"
    ).fetchone()
    if not row:
        return None
    episode = dict(row)
    conn.execute(
        "UPDATE episodes SET status = 'downloading' WHERE id = ? AND status = 'queued'",
        (episode["id"],),
    )
    conn.commit()
    # Verify we got it
    updated = conn.execute(
        "SELECT status FROM episodes WHERE id = ?", (episode["id"],)
    ).fetchone()
    if updated["status"] != "downloading":
        return None  # someone else grabbed it
    return episode


def _increment_403(conn: sqlite3.Connection) -> int:
    """Increment and return consecutive 403 counter."""
    val = conn.execute(
        "SELECT value FROM meta WHERE key = 'consecutive_403'"
    ).fetchone()
    count = int(val[0]) + 1 if val else 1
    conn.execute(
        "INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)",
        ("consecutive_403", str(count)),
    )
    conn.commit()
    if count >= 3:
        log.warning("Consecutive 403 count: %d — YouTube may be blocking", count)
    return count


def _reset_403(conn: sqlite3.Connection) -> None:
    conn.execute(
        "INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)",
        ("consecutive_403", "0"),
    )
    conn.commit()


def _download(episode: dict, conn: sqlite3.Connection) -> bool:
    """Run yt-dlp for a single episode. Returns True on success."""
    ep_id = episode["id"]
    url = episode["url"]
    out_template = str(Path(_AUDIO_DIR) / f"{ep_id}.%(ext)s")

    cmd = [
        "yt-dlp",
        "--cookies-from-browser", "firefox",
        "--extract-audio",
        "--audio-format", "m4a",
        "--audio-quality", "0",
        "--embed-thumbnail",
        "--add-metadata",
        "--write-info-json",
        "--output", out_template,
        "--no-playlist",
        "--max-filesize", "500M",
        "--retries", "3",
        "--fragment-retries", "3",
        "--abort-on-error",
        "--no-warnings",
        url,
    ]

    log.info("Downloading %s (%s)", ep_id, url)
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)

    if result.returncode != 0:
        stderr = result.stderr.strip()
        log.error("yt-dlp failed for %s: %s", ep_id, stderr[:500])
        is_403 = "403" in stderr or "Forbidden" in stderr
        if is_403:
            _increment_403(conn)
        else:
            _reset_403(conn)
        conn.execute(
            "UPDATE episodes SET status = 'failed', error_message = ? WHERE id = ?",
            (stderr[:1000], ep_id),
        )
        conn.commit()
        return False

    _reset_403(conn)

    # Find and parse info json
    info_path = Path(_AUDIO_DIR) / f"{ep_id}.info.json"
    title = None
    duration = None
    thumbnail = None
    if info_path.exists():
        try:
            info = json.loads(info_path.read_text())
            title = info.get("title")
            duration = info.get("duration")
            thumbnail = info.get("thumbnail")
        except Exception:
            log.warning("Failed to parse info.json for %s", ep_id)

    # Find the audio file
    audio_path = Path(_AUDIO_DIR) / f"{ep_id}.m4a"
    filesize = audio_path.stat().st_size if audio_path.exists() else 0

    conn.execute(
        """UPDATE episodes SET
            status = 'downloaded',
            title = ?,
            duration = ?,
            thumbnail_url = ?,
            filesize = ?,
            downloaded_at = ?,
            error_message = NULL
        WHERE id = ?""",
        (title, duration, thumbnail, filesize,
         datetime.now(timezone.utc).isoformat(), ep_id),
    )
    conn.commit()
    log.info("Downloaded %s: %s (%d bytes)", ep_id, title, filesize)
    return True


def _regenerate_feed_sync() -> None:
    """Regenerate RSS feed from downloaded episodes (sync version)."""
    conn = _get_sync_db()
    try:
        rows = conn.execute(
            "SELECT * FROM episodes WHERE status = 'downloaded' ORDER BY downloaded_at DESC"
        ).fetchall()
    finally:
        conn.close()

    from feedgen.feed import FeedGenerator
    fg = FeedGenerator()
    fg.load_extension("podcast")
    fg.title("PocketStream")
    fg.link(href=f"{_BASE_URL}/api/feed.xml", rel="self")
    fg.link(href=_BASE_URL)
    fg.description("YouTube to Podcast Proxy")
    fg.podcast.itunes_category("Technology")
    fg.podcast.itunes_explicit("no")
    fg.language("en")

    for row in rows:
        ep = dict(row)
        fe = fg.add_entry()
        fe.id(ep["id"])
        fe.title(ep.get("title") or ep["video_id"])
        fe.link(href=ep["url"])
        fe.description(f"From {ep['url']}")

        pub_date = ep.get("downloaded_at") or ep.get("created_at")
        if pub_date:
            if isinstance(pub_date, str):
                pub_date = datetime.fromisoformat(pub_date).replace(tzinfo=timezone.utc)
            fe.published(pub_date)

        audio_url = f"{_BASE_URL}/audio/{ep['id']}.m4a"
        filesize = ep.get("filesize") or 0
        duration = ep.get("duration") or 0
        fe.enclosure(audio_url, str(filesize), "audio/mp4")
        if duration:
            fe.podcast.itunes_duration(str(duration))
        thumbnail = ep.get("thumbnail_url")
        if thumbnail:
            fe.podcast.itunes_image(thumbnail)

    Path(_DATA_DIR).mkdir(parents=True, exist_ok=True)
    rss_str = fg.rss_str(pretty=True).decode("utf-8")
    Path(_DATA_DIR).joinpath("feed.xml").write_text(rss_str)
    log.info("Feed regenerated (sync) with %d episodes", len(rows))


def worker_main() -> None:
    """Entry point for the worker process."""
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
    )
    log.info("Worker starting up")

    conn = _get_sync_db()
    _startup_recovery(conn)

    last_heartbeat = time.monotonic()

    while True:
        try:
            now = time.monotonic()

            # Heartbeat every 30s
            if now - last_heartbeat >= 30:
                _heartbeat(conn)
                last_heartbeat = now

            # Try to claim work
            episode = _claim_next(conn)
            if episode:
                success = _download(episode, conn)
                # Regenerate feed regardless (success or failure changes visible state)
                try:
                    _regenerate_feed_sync()
                except Exception:
                    log.exception("Feed regeneration failed")
            else:
                time.sleep(5)

        except KeyboardInterrupt:
            log.info("Worker shutting down")
            break
        except Exception:
            log.exception("Worker error, sleeping 30s")
            time.sleep(30)
    conn.close()
