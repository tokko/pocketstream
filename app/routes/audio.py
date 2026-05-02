from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse

from ..config import settings
from ..db import get_db, get_episode_by_id
from ..middleware.auth import verify_basic_auth

router = APIRouter()


@router.get("/audio/{episode_id}.m4a")
async def serve_audio(episode_id: str, _auth=Depends(verify_basic_auth)):
    db = await get_db()
    try:
        episode = await get_episode_by_id(db, episode_id)
    finally:
        await db.close()

    if not episode or episode["status"] != "downloaded":
        raise HTTPException(404, "Episode not found")

    audio_path = settings.audio_dir / f"{episode_id}.m4a"
    if not audio_path.exists():
        raise HTTPException(404, "Audio file missing")

    return FileResponse(
        str(audio_path),
        media_type="audio/mp4",
        filename=f"{episode.get('title', episode_id)}.m4a",
    )
