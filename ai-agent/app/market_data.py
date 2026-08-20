"""
Simulated market data feed for the OpenEx AI agent service - Day 11 catch-up.

Generates a synthetic price series via a random walk with drift (no real
market data - this is a training/capstone environment) and exposes it as a
REST endpoint the React frontend can chart against (Day 14).

The series is generated once at import time and held in memory; each call
to /api/market-data appends exactly one new simulated tick before
returning the full history. This gives the frontend a feed that visibly
moves between polls without needing a background scheduler/websocket for
this service.
"""
from datetime import datetime, timedelta, timezone

import numpy as np
import pandas as pd
from fastapi import APIRouter

router = APIRouter()

SYMBOL = "BTC-SIM"
_STARTING_PRICE = 45000.0
_DRIFT = 0.0002       # slight upward drift per tick
_VOLATILITY = 0.004   # per-tick std dev, as a fraction of price
_MA_WINDOW = 10
_SEED_TICKS = 200
_TICK_INTERVAL = timedelta(minutes=1)


def _generate_seed_history() -> pd.DataFrame:
    """
    Build the initial random-walk price history: `_SEED_TICKS` ticks ending
    now, one `_TICK_INTERVAL` apart, each step drawn from a normal
    distribution with drift - a standard random-walk-with-drift model.
    """
    rng = np.random.default_rng()
    returns = rng.normal(loc=_DRIFT, scale=_VOLATILITY, size=_SEED_TICKS)
    prices = _STARTING_PRICE * np.cumprod(1 + returns)

    now = datetime.now(timezone.utc)
    timestamps = [now - _TICK_INTERVAL * (_SEED_TICKS - i) for i in range(_SEED_TICKS)]

    df = pd.DataFrame({"timestamp": timestamps, "price": prices})
    df["moving_average"] = df["price"].rolling(window=_MA_WINDOW, min_periods=1).mean()
    return df


# Module-level state: seeded once when the service starts, then appended to
# on each request. Simple and fine for a single-process dev/capstone
# deployment; would need a shared store (Redis/DB) behind multiple workers.
_history = _generate_seed_history()


def _append_tick() -> pd.DataFrame:
    global _history
    rng = np.random.default_rng()
    last_price = _history["price"].iloc[-1]
    step = rng.normal(loc=_DRIFT, scale=_VOLATILITY)
    new_price = last_price * (1 + step)
    new_row = pd.DataFrame({
        "timestamp": [datetime.now(timezone.utc)],
        "price": [new_price],
    })
    _history = (
        pd.concat([_history, new_row], ignore_index=True)
        .tail(_SEED_TICKS)
        .reset_index(drop=True)
    )
    _history["moving_average"] = _history["price"].rolling(window=_MA_WINDOW, min_periods=1).mean()
    return _history


@router.get("/api/market-data")
async def get_market_data():
    """
    Return the simulated price history (with rolling moving average) plus
    the current tick, as clean JSON arrays - matches the Day 11 deliverable:
    a REST endpoint returning historical and current market ticks.
    """
    df = _append_tick()
    history = [
        {
            "timestamp": row.timestamp.isoformat(),
            "price": round(row.price, 2),
            "moving_average": round(row.moving_average, 2),
        }
        for row in df.itertuples(index=False)
    ]
    return {
        "symbol": SYMBOL,
        "current": history[-1],
        "history": history,
    }
