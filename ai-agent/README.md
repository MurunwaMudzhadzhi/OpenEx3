# OpenEx AI Agent

Week 3's "astromech droid" — a Python + LangChain service running on Ollama.

## Day 1 scope

This is currently just a scaffold: FastAPI app, Docker container wired into
the main `docker-compose.yml`, and `/health` + `/ready` endpoints that
confirm connectivity to Ollama and report whether the configured model is
pulled. The actual LangChain agent (reading wallet balances, answering
questions) comes in a later day, built on top of this.

## Running it

From the project root (not this folder):

```bash
docker compose up --build -d
```

This brings up `ollama` and `ai-agent` alongside the existing Postgres/
backend/frontend stack.

## Pulling a model

On a fresh `openex_ollama_data` volume, Ollama starts with no models pulled
— pulling one is a first-run manual step, not something the container does
automatically (pulling a model on every `docker compose up` would be slow
and wasteful once you already have it). Since that volume persists across
restarts, subsequent starts reuse whatever you already pulled — you only
need to do this once per volume, not once per `docker compose up`.

```bash
docker exec -it openex-ollama ollama pull llama3.2:1b
```

`llama3.2:1b` is deliberately small — fast enough for CPU inference on a
dev machine. If you have GPU support configured or don't mind slower
responses, a larger model (e.g. `llama3.2:3b` or `llama3.1:8b`) will give
better answers once the agent logic exists. Set `OLLAMA_MODEL` in
`docker-compose.yml` to match whatever you pull for the containerized
`ai-agent` service — see "Local development" below for the equivalent when
running outside Docker.

## Checking it worked

```bash
curl http://localhost:8000/health
```

`"status": "ok"` means Ollama is reachable and the configured model is
pulled. `"status": "degraded"` with a `detail` message tells you which of
those isn't true yet — most commonly, the model just hasn't been pulled.
`/health` always returns 200 (it's a liveness check — "is the process up");
`/ready` returns 503 instead of 200 while degraded, and is what
`docker-compose.yml`'s healthcheck actually calls.

## Local development (outside Docker)

The `ai-agent` container reaches Ollama over the Compose network, so
`docker-compose.yml` deliberately doesn't publish Ollama's port to the
host. Running this service outside Docker needs some way to reach Ollama
from your machine instead — either temporarily add a `ports: ["11434:11434"]`
mapping back to the `ollama` service in `docker-compose.yml`, or install
Ollama natively.

**macOS/Linux (bash):**
```bash
cd ai-agent
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
OLLAMA_HOST=http://localhost:11434 OLLAMA_MODEL=llama3.2:1b uvicorn app.main:app --reload
```

**Windows (PowerShell):**
```powershell
cd ai-agent
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:OLLAMA_HOST = "http://localhost:11434"
$env:OLLAMA_MODEL = "llama3.2:1b"
uvicorn app.main:app --reload
```

**Windows (Command Prompt):**
```cmd
cd ai-agent
python -m venv .venv
.venv\Scripts\activate.bat
pip install -r requirements.txt
set OLLAMA_HOST=http://localhost:11434
set OLLAMA_MODEL=llama3.2:1b
uvicorn app.main:app --reload
```

`OLLAMA_MODEL` defaults to `llama3.2:1b` (see `app/config.py`) — only set it
explicitly if you pulled a different model. Note this env var is read by
the local `uvicorn` process directly; it's independent of whatever
`OLLAMA_MODEL` is set to in `docker-compose.yml` for the containerized run.

## Tests

```bash
cd ai-agent
pytest
```
