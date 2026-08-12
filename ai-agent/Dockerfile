FROM python:3.12-slim

WORKDIR /app
RUN useradd --system --create-home --uid 10001 appuser

# Install deps first for better layer caching — only re-runs pip install
# when requirements.txt actually changes, not on every code edit.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
USER appuser

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
