# --- Build stage ---
FROM python:3.12-alpine AS builder

RUN apk add --no-cache gcc musl-dev libffi-dev

RUN pip install --no-cache-dir --prefix=/install \
    fastapi \
    uvicorn[standard] \
    aiosqlite \
    feedgen \
    pydantic-settings \
    yt-dlp

# --- Runtime stage ---
FROM python:3.12-alpine

RUN apk add --no-cache ffmpeg && \
    rm -rf /var/cache/apk/* /tmp/*

COPY --from=builder /install /usr/local

WORKDIR /app
COPY app/ ./app/

EXPOSE 8080

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
