# OpenEx AI Agent

Week 3's "astromech droid" — a Python + LangChain service running on Ollama.

## Day 1 scope

This is currently just a scaffold: FastAPI app, Docker container wired into
the main `docker-compose.yml`, and a `/health` endpoint that confirms
connectivity to Ollama and reports whether the configured model is pulled.
The actual LangChain agent (reading wallet balances, answering questions)
comes in a later day, built on top of this.

## Running it

From the project root (not this folder):

```bash
docker compose up --build -d
```

This brings up `ollama` and `ai-agent` alongside the existing Postgres/
backend/frontend stack.

## Pulling a model

Ollama starts with no models pulled — that's a one-time manual step, not
something the container does automatically (pulling a model on every
`docker compose up` would be slow and wasteful once you already have it).

```bash
docker exec -it openex-ollama ollama pull llama3.2:1b
```

`llama3.2:1b` is deliberately small — fast enough for CPU inference on a
dev machine. If you have GPU support configured or don't mind slower
responses, a larger model (e.g. `llama3.2:3b` or `llama3.1:8b`) will give
better answers once the agent logic exists. Set `OLLAMA_MODEL` in
`docker-compose.yml` to match whatever you pull.

## Checking it worked

```bash
curl http://localhost:8000/health
```

`"status": "ok"` means Ollama is reachable and the configured model is
pulled. `"status": "degraded"` with a `detail` message tells you which of
those isn't true yet — most commonly, the model just hasn't been pulled.

## Local development (outside Docker)

```bash
cd ai-agent
python -m venv .venv
source .venv/bin/activate   # or .venv\Scripts\activate on Windows
pip install -r requirements.txt
OLLAMA_HOST=http://localhost:11434 uvicorn app.main:app --reload
```

This assumes Ollama itself is still running via Docker (or installed
natively) and reachable at `localhost:11434`.

## Tests

```bash
cd ai-agent
pytest
```
