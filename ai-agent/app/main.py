"""
OpenEx AI agent service.

Day 1: FastAPI scaffold with health/readiness checks against Ollama.
Day 2: POST /chat - LangChain ChatOllama call with a financial persona
system prompt (see app/agent.py).
Day 3: /chat now forwards the caller's Authorization header through, so
the agent can use the wallet-balance tool (also in app/agent.py) to answer
real balance questions via the Kotlin backend's GET /accounts endpoint.

CodeRabbit fix: get_chat_response() errors are now caught and mapped to
503 (Ollama unreachable/transport error) or 504 (timeout) instead of
surfacing as a raw 500.
"""
import httpx
from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app.agent import get_chat_response
from app.market_data import router as market_data_router
from app.config import settings
from app.ollama_status import check_ollama

app = FastAPI(title="OpenEx AI Agent", version="0.4.0")

app.include_router(market_data_router)


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
async def chat(request: ChatRequest, authorization: str | None = Header(default=None)):
    """
    Send a message to the AI trading assistant and get a reply back.

    If the caller sends an Authorization header (the same JWT they use
    against the Kotlin backend), it's forwarded through so the agent can
    use the wallet-balance tool and answer real balance questions. Without
    it, the assistant still answers general trading questions - it just
    can't look anything up.

    Fails with 503 if Ollama itself isn't reachable/ready, 503 if a chat
    call transport-fails mid-request, and 504 if it times out - each a
    dependency-down condition the client should treat differently from a
    genuine server error (which still propagates as 500).
    """
    ollama_status = await check_ollama()
    if not (ollama_status.reachable and ollama_status.model_available):
        raise HTTPException(status_code=503, detail=f"AI model unavailable: {ollama_status.detail}")

    try:
        reply = await get_chat_response(request.message, auth_token=authorization)
    except httpx.TimeoutException as exc:
        raise HTTPException(status_code=504, detail=f"AI model timed out: {exc}") from exc
    except httpx.HTTPError as exc:
        raise HTTPException(status_code=503, detail=f"AI model unavailable: {exc}") from exc

    return ChatResponse(reply=reply)


@app.get("/")
async def root():
    return {"service": "OpenEx AI Agent", "docs": "/docs"}

