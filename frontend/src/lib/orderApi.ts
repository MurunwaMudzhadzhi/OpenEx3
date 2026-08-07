export type OrderSide = "BUY" | "SELL";
export type OrderType = "LIMIT" | "MARKET";

export interface SubmitOrderInput {
  symbol: string;
  side: OrderSide;
  type: OrderType;
  price?: string; // required for LIMIT, omitted for MARKET
  quantity: string;
}

export interface TradeSummary {
  tradeId: string;
  price: string;
  quantity: string;
  executedAt: string;
}

export interface OrderResponse {
  orderId: string;
  symbol: string;
  side: OrderSide;
  type: OrderType;
  price: string | null;
  quantity: string;
  filledQuantity: string;
  status: string;
  trades: TradeSummary[];
}

export interface OrderApiError {
  error: string;
  message: string | null;
}

/**
 * Submits an order as the currently logged-in user. The backend derives
 * the account from the JWT — there's no userId field to fill in here.
 * A fresh Idempotency-Key is generated per call, so retrying the exact
 * same submitOrder() call (e.g. a naive "retry on network error") would
 * double-submit; that's a deliberate choice — an accidental double-click
 * on the same rendered button is what idempotency here guards against,
 * not an automatic retry loop, which should generate its own key reuse
 * logic if it's ever added.
 */
export async function submitOrder(token: string, input: SubmitOrderInput): Promise<OrderResponse> {
  const res = await fetch("/orders", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`,
      "Idempotency-Key": crypto.randomUUID(),
    },
    body: JSON.stringify(input),
  });

  const body = await res.json();

  if (!res.ok) {
    const err = body as OrderApiError;
    throw new Error(err.message ?? err.error ?? "Order submission failed");
  }

  return body as OrderResponse;
}
