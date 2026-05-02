# PocketStream

Self-hosted YouTube-to-podcast proxy with an Android companion app.

Share YouTube videos from any app → PocketStream downloads the audio → listen in any podcast player via RSS feed.

## Architecture

- **Backend:** Python/FastAPI + yt-dlp worker. Docker-ready.
- **Android App:** Kotlin + Jetpack Compose. Share-intent receiver.

## Quick Start (Backend)

```bash
# 1. Clone the repo
git clone https://github.com/tokko/pocketstream.git
cd pocketstream

# 2. Configure
cp .env.example .env
# Edit .env with your settings

# 3. Launch
docker compose up -d

# 4. Podcast feed available at
# http://your-host:8080/api/feed.xml
```

## Configuration (.env)

Copy `.env.example` to `.env` and configure:

| Variable | Default | Description |
|---|---|---|
| `POCKETSTREAM_BASE_URL` | `http://192.168.68.110:8080` | Public base URL for RSS feed links |
| `POCKETSTREAM_API_KEY` | `change-me` | API key for the `/api/enqueue` endpoint |
| `POCKETSTREAM_BASIC_USER` | `pocket` | Basic auth username for feed access |
| `POCKETSTREAM_BASIC_PASS` | `change-me` | Basic auth password for feed access |
| `POCKETSTREAM_MAX_STORAGE_GB` | `50` | Max storage for downloaded audio (GB) |
| `POCKETSTREAM_MAX_QUEUE_SIZE` | `50` | Max items in the download queue |
| `POCKETSTREAM_DATA_DIR` | `/data` | Data directory for DB, audio, and feed |

## Network & Security

PocketStream is designed for **LAN + Tailscale** access. No authentication is required beyond the API key for enqueue.

**Recommendations:**
- Use [Tailscale](https://tailscale.com) or a similar mesh VPN to connect your phone to the backend
- Do **not** expose the backend directly to the public internet without adding proper auth
- The RSS feed (`/api/feed.xml`) and audio files are served without auth so podcast players can access them
- All traffic is HTTP by default — wrap in TLS if you need encryption

## Feed URL

Once the backend is running, add this URL to any podcast app:

```
http://your-host:8080/api/feed.xml
```

The Android companion app has a "Copy RSS Link" button to grab this easily.

## Android App

### Building

1. Open the `android/` directory in Android Studio
2. Let Gradle sync and download dependencies
3. Build & run on your device or emulator

### Setup

1. Open the app → tap the **Settings** FAB
2. Enter your **Backend URL** (e.g. `http://192.168.68.110:8080`)
3. Enter your **API Key** (matches `POCKETSTREAM_API_KEY` in `.env`)
4. Tap **Test Connection** to verify

### Usage

- **Share a YouTube video** from any app (YouTube, Chrome, etc.)
- Select **PocketStream** from the share sheet
- The video is enqueued for download automatically
- A snackbar confirms success or shows the error
- Audio appears in your podcast RSS feed once downloaded

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/enqueue` | X-API-Key header | Enqueue a YouTube URL for download |
| `GET` | `/api/feed.xml` | Basic Auth | RSS podcast feed |
| `GET` | `/audio/{id}.m4a` | Basic Auth | Download audio file |
| `GET` | `/health` | None | Health check |

### Enqueue Request

```json
POST /api/enqueue
X-API-Key: your-key
Content-Type: application/json

{"url": "https://youtube.com/watch?v=dQw4w9WgXcQ"}
```

### Enqueue Response

```json
{"id": "abc123", "status": "queued"}
```

Or if already exists:

```json
{"id": "abc123", "status": "downloaded", "message": "already exists"}
```

## License

MIT
