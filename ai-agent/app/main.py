"""
OpenEx AI agent service.

Day 1: FastAPI scaffold with health/readiness checks against Ollama.
Day 2: adds POST /chat - a LangChain ChatOllama call with a financial
persona system prompt (see app/agent.py). No tool calling yet; the agent
can't read real wallet balances or place orders - that's Day 3, built on
top of this rather than replacing it.
"""
from fastapi import FastAPI, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.agent import get_chat_response
from app.config import settings
from app.ollama_status import check_ollama

app = FastAPI(title="OpenEx AI Agent", version="0.2.0")


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
    Liveness check. Always returns 200 if the process itself is up - this
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
    the configured model is pulled - this is what Docker's healthcheck
    should call, so the container isn't reported healthy while dependent
    services are still warming up or unconfigured.
    """
    is_ready, body = _status_body(await check_ollama())
    if is_ready:
        return body
    return JSONResponse(status_code=503, content=body)


class ChatRequest(BaseModel):
    message: str = Field(..., min_length=1, description="The user's message to the AI assistant")


class ChatResponse(BaseModel):
    reply: str


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """
    Send a message to the AI trading assistant and get a reply back.

    Fails with 503 (not a raw 500) if Ollama itself isn't reachable/ready -
    that's a dependency-down condition the client should treat differently
    from a genuine server error.
    """
    ollama_status = await check_ollama()
    if not (ollama_status.reachable and ollama_status.model_available):
        raise HTTPException(status_code=503, detail=f"AI model unavailable: {ollama_status.detail}")

    reply = await get_chat_response(request.message)
    return ChatResponse(reply=reply)


@app.get("/")
async def root():
    return {"service": "OpenEx AI Agent", "docs": "/docs"}
