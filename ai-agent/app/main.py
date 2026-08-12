"""
OpenEx AI agent service — Day 1 scaffold.

This is intentionally minimal: a FastAPI app with a health endpoint that
confirms the service is up AND that it can actually reach Ollama with the
configured model available. The LangChain agent itself (reading wallet
balances, answering questions) comes in a later day, built on top of this
scaffold rather than replacing it.
"""
from fastapi import FastAPI

from app.config import settings
from app.ollama_status import check_ollama

app = FastAPI(title="OpenEx AI Agent", version="0.1.0")


@app.get("/health")
async def health():
    """
    Liveness/readiness check.

    Returns 200 with status details either way — "degraded" isn't an error
    response, it's a valid state to report (e.g. Ollama still warming up,
    or the model hasn't been pulled yet). Docker's healthcheck can key off
    the JSON body's "status" field, or just the 200 itself for basic
    liveness, depending on how strict we want startup gating to be.
    """
    ollama = await check_ollama()

    status = "ok" if (ollama.reachable and ollama.model_available) else "degraded"

    return {
        "status": status,
        "service": "openex-ai-agent",
        "ollama": {
            "reachable": ollama.reachable,
            "model": settings.ollama_model,
            "model_available": ollama.model_available,
            "detail": ollama.detail,
        },
    }


@app.get("/")
async def root():
    return {"service": "OpenEx AI Agent", "docs": "/docs"}
