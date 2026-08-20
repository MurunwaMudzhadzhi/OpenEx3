"""
LangChain agent setup for the OpenEx AI assistant.

Day 2: plain ChatOllama call with a financial persona system prompt.
Day 3: adds a wallet-balance tool. The tool calls the Kotlin backend's
GET /accounts endpoint using the SAME JWT the caller sent to /chat - the
agent never gets its own credentials, it can only see what the logged-in
user can see. Because the token is per-request, the tool (and the agent
that uses it) are built per-request via a small factory rather than once
at import time.
"""
import httpx
from langchain.agents import create_agent
from langchain.tools import tool
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_ollama import ChatOllama

from app.config import settings

SYSTEM_PROMPT = (
    "You are Vex, the AI trading assistant embedded in OpenEx, a simulated "
    "crypto exchange terminal used for a training/capstone environment.\n\n"
    "TOOL USE RULE (follow exactly): call get_wallet_balances ONLY if the "
    "user's message explicitly asks about THEIR OWN balance, funds, "
    "holdings, portfolio, or 'how much [asset] do I have'. For every other "
    "question - including general questions about order types, the order "
    "book, price-time priority, spreads, volatility, or how trading works "
    "- do NOT call any tool. Just answer directly from what you know below. "
    "If you are unsure whether a question is about the user's own balance, "
    "assume it is NOT and answer directly instead.\n\n"
    "REFERENCE DEFINITIONS (use these, don't improvise):\n"
    "- Limit order: an order to buy/sell at a specific price or better; it "
    "may not fill immediately.\n"
    "- Market order: an order to buy/sell immediately at the best "
    "currently available price.\n"
    "- Order book: the live list of all open buy (bid) and sell (ask) "
    "orders for a symbol, sorted by price.\n"
    "- Price-time priority: when multiple orders share the same price, the "
    "order placed EARLIEST (first in time) at that price is filled first.\n"
    "- Bid-ask spread: the gap between the highest current buy price (bid) "
    "and the lowest current sell price (ask).\n"
    "- Volatility: how much and how quickly an asset's price moves over "
    "time; higher volatility means larger, faster price swings.\n\n"
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


def _make_wallet_tool(auth_token: str):
    """
    Build a get_wallet_balances tool bound to one caller's JWT via closure.
    Rebuilt per-request (not cached) because the token differs per user and
    per login session.
    """

    @tool
    async def get_wallet_balances() -> str:
        """Fetch the current user's simulated wallet balances (e.g. USD, BTC)
        from the OpenEx backend. Use this whenever the user asks about their
        balance, funds, portfolio, or how much of an asset they hold."""
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(
                    f"{settings.backend_url}/accounts",
                    headers={"Authorization": auth_token},
                )
                response.raise_for_status()
        except httpx.HTTPError as exc:
            return f"Could not fetch balances right now: {exc}"

        balances = response.json()
        if not balances:
            return "The user currently has no balances on record."
        return ", ".join(f"{b['balance']} {b['asset']}" for b in balances)

    return get_wallet_balances


# Keywords that indicate the user is asking about THEIR OWN wallet/balance.
# Small local models (llama3.2:1b) are unreliable at following a purely
# prompted "don't call this tool unless..." instruction - they tend to
# pattern-match on loosely related words (e.g. "order" in "order types")
# and call the tool anyway. Deciding tool availability here, in code,
# instead of trusting the model's judgment, makes the behavior
# deterministic regardless of model size.
_BALANCE_TERMS = (
    "balance", "balances", "funds", "wallet", "portfolio", "holdings", "account",
)

_OWNERSHIP_PHRASES = ("my ", "do i have", "i have")


def _mentions_own_balance(user_message: str) -> bool:
    lowered = user_message.lower()
    return (
        any(term in lowered for term in _BALANCE_TERMS)
        and any(phrase in lowered for phrase in _OWNERSHIP_PHRASES)
    )


async def get_chat_response(user_message: str, auth_token: str | None = None) -> str:
    """
    Send a message to the assistant and return its reply text.

    The wallet-balance tool is only bound into the agent when auth_token is
    present AND the message itself looks like a balance/funds question
    (see _mentions_own_balance). This keeps tool-triggering deterministic
    instead of relying on a small local model to correctly judge relevance
    on its own - for anything else (general trading questions), the plain
    no-tool chat path is used, which is also faster since it skips the
    agent/tool-graph overhead entirely.
    """
    if auth_token and _mentions_own_balance(user_message):
        wallet_tool = _make_wallet_tool(auth_token)
        agent = create_agent(model=_llm, tools=[wallet_tool], system_prompt=SYSTEM_PROMPT)
        result = await agent.ainvoke({"messages": [{"role": "user", "content": user_message}]})
        return result["messages"][-1].content

    messages = [
        SystemMessage(content=SYSTEM_PROMPT),
        HumanMessage(content=user_message),
    ]
    response = await _llm.ainvoke(messages)
    return response.content


