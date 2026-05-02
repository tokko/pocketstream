import logging
from datetime import datetime, timezone
from pathlib import Path

from feedgen.feed import FeedGenerator

from .config import settings
from .db import get_db, get_downloaded_episodes

log = logging.getLogger("pocketstream.rss")


async def regenerate_feed() -> None:
    """Regenerate the RSS XML file from downloaded episodes."""
    db = await get_db()
    try:
        episodes = await get_downloaded_episodes(db)
    finally:
        await db.close()

    fg = FeedGenerator()
    fg.load_extension("podcast")
    fg.title("PocketStream")
    fg.link(href=f"{settings.base_url}/api/feed.xml", rel="self")
    fg.link(href=settings.base_url)
    fg.description("YouTube to Podcast Proxy")
    fg.podcast.itunes_category("Technology")
    fg.podcast.itunes_explicit("no")
    fg.language("en")

    for ep in episodes:
        fe = fg.add_entry()
        fe.id(ep["id"])
        title = ep.get("title") or ep["video_id"]
        fe.title(title)
        fe.link(href=ep["url"])
        fe.description(f"From {ep['url']}")

        pub_date = ep.get("downloaded_at") or ep.get("created_at")
        if pub_date:
            fe.published(pub_date if isinstance(pub_date, datetime) else datetime.fromisoformat(pub_date).replace(tzinfo=timezone.utc))

        # Enclosure
        audio_url = f"{settings.base_url}/audio/{ep['id']}.m4a"
        filesize = ep.get("filesize") or 0
        duration = ep.get("duration") or 0
        fe.enclosure(audio_url, str(filesize), "audio/mp4")

        if duration:
            fe.podcast.itunes_duration(str(duration))

        thumbnail = ep.get("thumbnail_url")
        if thumbnail:
            fe.podcast.itunes_image(thumbnail)

    # Write to file
    settings.data_dir.mkdir(parents=True, exist_ok=True)
    rss_str = fg.rss_str(pretty=True).decode("utf-8")
    settings.feed_path.write_text(rss_str)
    log.info("Feed regenerated with %d episodes", len(episodes))
