"""
Day 1 tests: just confirm the service boots and the health endpoint reports
the expected shape. Ollama connectivity itself isn't mocked/asserted here —
that's an integration concern (does the real Ollama container + model
exist), not something worth mocking out at this stage. A later day can add
a mocked check_ollama() test once there's actual agent logic depending on
its result.
"""
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_root_returns_service_info():
    response = client.get("/")
    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "OpenEx AI Agent"


def test_health_returns_expected_shape():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()

    assert body["status"] in ("ok", "degraded")
    assert body["service"] == "openex-ai-agent"
    assert "reachable" in body["ollama"]
    assert "model_available" in body["ollama"]
