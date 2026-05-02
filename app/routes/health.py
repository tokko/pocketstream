import os
import time
from pathlib import Path

from fastapi import APIRouter

from ..config import settings

router = APIRouter()


@router.get("/health")
async def health():
    now = time.time()
    checks = {}

    # Cookie DB check
    cookie_dir = Path("/root/.mozilla/firefox")
    cookie_found = False
    cookie_age = None
    if cookie_dir.exists():
        for profile in cookie_dir.iterdir():
            db_file = profile / "cookies.sqlite"
            if db_file.exists():
                cookie_found = True
                cookie_age = now - db_file.stat().st_mtime
                break
    checks["cookie_db"] = "ok" if cookie_found else "MISSING"
    if cookie_age is not None:
        checks["cookie_age_hours"] = round(cookie_age / 3600, 1)

    # Disk usage
    disk = os.statvfs(str(settings.data_dir))
    total_gb = (disk.f_blocks * disk.f_frsize) / (1024**3)
    free_gb = (disk.f_bavail * disk.f_frsize) / (1024**3)
    checks["disk_total_gb"] = round(total_gb, 1)
    checks["disk_free_gb"] = round(free_gb, 1)

    # Audio dir size
    audio_size = 0
    audio_count = 0
    if settings.audio_dir.exists():
        for f in settings.audio_dir.iterdir():
            if f.is_file() and f.suffix == ".m4a":
                audio_size += f.stat().st_size
                audio_count += 1
    checks["audio_files"] = audio_count
    checks["audio_size_mb"] = round(audio_size / (1024**2), 1)

    return {"status": "ok", "checks": checks}
