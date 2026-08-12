"""
OpenEx AI agent service — Day 1 scaffold.

This is intentionally minimal: a FastAPI app with a health endpoint that
confirms the service is up AND that it can actually reach Ollama with the
configured model available. The LangChain agent itself (reading wallet
balances, answering questions) comes in a later day, built on top of this
scaffold rather than replacing it.
"""
from fastapi import FastAPI
from fastapi.responses import JSONResponse

from app.config import settings
from app.ollama_status import check_ollama

app = FastAPI(title="OpenEx AI Agent", version="0.1.0")


def _status_body(ollama):
    ready = ollama.reachable and ollama.model_available
    return ready, {
        "status": "ok" if ready else "degraded",
        "service": "openex-ai-agent",
        "ollama": {
            "reachable": ollama.reachable,
            "model": settings.ollama_model,
            "model_available": ollama.model_available,
            "detail": ollama.detail,
        },
    }


@app.get("/health")
async def health():
    """
    Liveness check. Always returns 200 if the process itself is up — this
    answers "is the service running", not "is it ready to serve traffic".
    Use /ready for the latter; Docker's healthcheck should call /ready, not
    this endpoint, or it will report the container healthy before Ollama
    and the model are actually available.
    """
    _, body = _status_body(await check_ollama())
    return body


@app.get("/ready")
async def ready():
    """
    Readiness check. Returns 503 (not 200) unless Ollama is reachable AND
    the configured model is pulled — this is what Docker's healthcheck
    should call, so the container isn't reported healthy while dependent
    services are still warming up or unconfigured.
    """
    is_ready, body = _status_body(await check_ollama())
    if is_ready:
        return body
    return JSONResponse(status_code=503, content=body)


@app.get("/")
async def root():
    return {"service": "OpenEx AI Agent", "docs": "/docs"}
