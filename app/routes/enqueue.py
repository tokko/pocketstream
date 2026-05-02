import re
import uuid

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from ..db import get_db, get_queue_depth, insert_episode, get_episode_by_video_id
from ..middleware.auth import verify_api_key

router = APIRouter()

_VIDEO_ID_RE = re.compile(
    r"(?:v=|/v/|youtu\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})"
)

_YOUTUBE_RE = re.compile(
    r"^https?://(www\.)?(youtube\.com|youtu\.be)/"
)


def extract_video_id(url: str) -> str | None:
    m = _VIDEO_ID_RE.search(url)
    return m.group(1) if m else None


def sanitize_url(url: str) -> str | None:
    """Validate it's a YouTube URL, strip tracking params, return clean URL."""
    if not _YOUTUBE_RE.match(url):
        return None
    vid = extract_video_id(url)
    if not vid:
        return None
    return f"https://www.youtube.com/watch?v={vid}"


class EnqueueRequest(BaseModel):
    url: str


@router.post("/api/enqueue")
async def enqueue(body: EnqueueRequest, _auth: str = Depends(verify_api_key)):
    clean_url = sanitize_url(body.url)
    if not clean_url:
        raise HTTPException(400, "Invalid YouTube URL")

    video_id = extract_video_id(clean_url)

    db = await get_db()
    try:
        # Check duplicate
        existing = await get_episode_by_video_id(db, video_id)
        if existing:
            return {"id": existing["id"], "status": existing["status"], "message": "already exists"}

        # Check queue depth
        from ..config import settings
        depth = await get_queue_depth(db)
        if depth >= settings.max_queue_size:
            raise HTTPException(429, f"Queue full ({depth}/{settings.max_queue_size})")

        episode_id = uuid.uuid4().hex[:12]
        await insert_episode(
            db,
            id=episode_id,
            video_id=video_id,
            url=clean_url,
            status="queued",
        )

        return {"id": episode_id, "status": "queued"}
    finally:
        await db.close()
