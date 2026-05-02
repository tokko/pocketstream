from pydantic_settings import BaseSettings
from pathlib import Path


class Settings(BaseSettings):
    base_url: str = "http://192.168.68.110:8080"
    api_key: str = "change-me"
    basic_user: str = "pocket"
    basic_pass: str = "change-me"
    max_storage_gb: int = 50
    max_queue_size: int = 50
    data_dir: Path = Path("/data")

    model_config = {"env_prefix": "POCKETSTREAM_", "env_file": ".env"}

    @property
    def audio_dir(self) -> Path:
        return self.data_dir / "audio"

    @property
    def db_path(self) -> Path:
        return self.data_dir / "pocketstream.db"

    @property
    def feed_path(self) -> Path:
        return self.data_dir / "feed.xml"


settings = Settings()
