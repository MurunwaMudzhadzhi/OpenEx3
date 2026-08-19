"""
LangChain agent setup for the OpenEx AI assistant - Day 2 (Ollama + LangChain).

This module wires ChatOllama up to a financial-trading-assistant persona.
It's deliberately just a chat model call for now (no tools bound) - tool
calling against the Kotlin backend (reading wallet balances) comes in Day 3,
built on top of this rather than replacing it.
"""
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_ollama import ChatOllama

from app.config import settings

SYSTEM_PROMPT = (
    "You are Vex, the AI trading assistant embedded in OpenEx, a simulated "
    "crypto exchange terminal used for a training/capstone environment. "
    "You help users understand their orders, balances, and general market "
    "concepts (order types, the order book, price-time priority matching, "
    "spreads, volatility). "
    "All funds, trades, and balances in this system are simulated - never "
    "imply you are giving real financial advice, and don't claim access to "
    "real markets or real money. Keep answers concise and trader-appropriate: "
    "clear, direct, and free of unnecessary hedging or disclaimers beyond "
    "what's genuinely useful."
)

# Built once at import time - creating a ChatOllama instance per request
# would reopen a client connection to Ollama on every call for no benefit.
_llm = ChatOllama(
    model=settings.ollama_model,
    base_url=settings.ollama_host,
    temperature=0.4,
)


async def get_chat_response(user_message: str) -> str:
    """
    Send a single user message to the model with the financial persona
    system prompt and return the assistant's reply text.

    No conversation memory yet - each call is stateless. Multi-turn history
    can be added later (e.g. accepting prior messages from the client) once
    the frontend chat panel needs it.
    """
    messages = [
        SystemMessage(content=SYSTEM_PROMPT),
        HumanMessage(content=user_message),
    ]
    response = await _llm.ainvoke(messages)
    return response.content
