"""
Thin wrapper around Ollama's HTTP API for connectivity/health checks.

This deliberately does NOT go through LangChain for the health check —
LangChain's Ollama integration is for the agent itself (later days); for
"is Ollama up and does it have our model," a plain HTTP call to Ollama's
own /api/tags endpoint is simpler and doesn't require constructing an
LLM/agent object just to answer a yes/no question.
"""
import httpx

from app.config import settings


class OllamaStatus:
    def __init__(self, reachable: bool, model_available: bool, detail: str | None = None):
        self.reachable = reachable
        self.model_available = model_available
        self.detail = detail


async def check_ollama() -> OllamaStatus:
    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.get(f"{settings.ollama_host}/api/tags")
            response.raise_for_status()
    except httpx.RequestError as exc:
        return OllamaStatus(reachable=False, model_available=False, detail=f"Could not reach Ollama: {exc}")
    except httpx.HTTPStatusError as exc:
        return OllamaStatus(
            reachable=True,
            model_available=False,
            detail=f"Ollama responded with an error status: {exc.response.status_code}",
        )

    models = [m.get("name", "") for m in response.json().get("models", [])]
    # Exact match — Ollama tags identify distinct models (e.g. "llama3.2:1b"
    # and "llama3.2:10b" are different models, not versions of each other),
    # so a prefix match would incorrectly treat one as satisfying the other.
    model_available = settings.ollama_model in models

    detail = None if model_available else (
        f"Ollama is reachable, but model '{settings.ollama_model}' isn't pulled yet. "
        f"Available: {models or '(none)'}"
    )

    return OllamaStatus(reachable=True, model_available=model_available, detail=detail)
