"""
Tests for /, /health, and /ready. check_ollama is mocked throughout so these
are true unit tests — they don't depend on a real Ollama instance being
reachable, and they can deterministically exercise both the "ready" and
"not ready" paths.
"""
from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from app.main import app
from app.ollama_status import OllamaStatus

client = TestClient(app)


def test_root_returns_service_info():
    response = client.get("/")
    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "OpenEx AI Agent"


@patch("app.main.check_ollama", new_callable=AsyncMock)
def test_health_returns_ok_when_ollama_ready(mock_check):
    mock_check.return_value = OllamaStatus(reachable=True, model_available=True)

    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "openex-ai-agent"
    assert body["ollama"]["reachable"] is True
    assert body["ollama"]["model_available"] is True


@patch("app.main.check_ollama", new_callable=AsyncMock)
def test_health_returns_degraded_but_still_200_when_ollama_not_ready(mock_check):
    mock_check.return_value = OllamaStatus(reachable=False, model_available=False, detail="unreachable")

    response = client.get("/health")
    assert response.status_code == 200  # liveness — always 200 while the process itself is up
    body = response.json()
    assert body["status"] == "degraded"


@patch("app.main.check_ollama", new_callable=AsyncMock)
def test_ready_returns_200_when_ollama_ready(mock_check):
    mock_check.return_value = OllamaStatus(reachable=True, model_available=True)

    response = client.get("/ready")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


@patch("app.main.check_ollama", new_callable=AsyncMock)
def test_ready_returns_503_when_ollama_not_ready(mock_check):
    mock_check.return_value = OllamaStatus(reachable=True, model_available=False, detail="model not pulled")

    response = client.get("/ready")
    assert response.status_code == 503
    assert response.json()["status"] == "degraded"
