"""
Configuration for the OpenEx AI agent service.

All values are read from environment variables (set via docker-compose),
with dev-friendly defaults so the service also runs standalone with
`uvicorn app.main:app` outside Docker.
"""
import os


class Settings:
    # Ollama connection. "ollama" is the docker-compose service name — from
    # inside the ai-agent container, that resolves via Docker's internal
    # DNS. Outside Docker (running this locally), override with
    # OLLAMA_HOST=http://localhost:11434.
    ollama_host: str = os.getenv("OLLAMA_HOST", "http://ollama:11434")

    # Deliberately small/fast model for a dev machine — this is a capstone
    # project, not a production inference deployment. Swap via env var if
    # you've pulled something else into Ollama.
    ollama_model: str = os.getenv("OLLAMA_MODEL", "llama3.2:1b")

    # The Spring Boot backend, for the wallet-balance-reading tool this
    # service will call in a later day. Not used yet in Day 1 — just wired
    # up now so config doesn't need to change later.
    backend_url: str = os.getenv("BACKEND_URL", "http://backend:8080")


settings = Settings()
