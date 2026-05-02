from pathlib import Path

from fastapi import APIRouter, Depends
from fastapi.responses import FileResponse

from ..config import settings
from ..middleware.auth import verify_basic_auth

router = APIRouter()


@router.get("/api/feed.xml")
async def feed_xml(_auth=Depends(verify_basic_auth)):
    return _serve_feed()


@router.get("/feed.xml")
async def feed_xml_no_auth():
    """Unauthenticated access for internal/debug."""
    return _serve_feed()


def _serve_feed():
    feed_path = settings.feed_path
    if not feed_path.exists():
        feed_path.parent.mkdir(parents=True, exist_ok=True)
        feed_path.write_text(
            '<?xml version="1.0" encoding="UTF-8"?>'
            '<rss version="2.0"><channel><title>PocketStream</title></channel></rss>'
        )
    stat = feed_path.stat()
    return FileResponse(
        str(feed_path),
        media_type="application/xml",
        headers={
            "ETag": f'"{stat.st_mtime_ns:x}"',
            "Last-Modified": _http_date(stat.st_mtime),
        },
    )


def _http_date(ts: float) -> str:
    from datetime import datetime, timezone
    dt = datetime.fromtimestamp(ts, tz=timezone.utc)
    return dt.strftime("%a, %d %b %Y %H:%M:%S GMT")
