import type { OrderSide, OrderType } from "./orderApi";

export interface Balance {
  asset: string;
  balance: string;
}

export interface OpenOrder {
  orderId: string;
  symbol: string;
  side: OrderSide;
  type: OrderType;
  price: string | null;
  quantity: string;
  filledQuantity: string;
  status: string;
}

async function authedGet<T>(path: string, token: string): Promise<T> {
  const res = await fetch(path, {
    headers: { "Authorization": `Bearer ${token}` },
  });
  if (!res.ok) {
    throw new Error(`Request to ${path} failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}

export function fetchMyBalances(token: string): Promise<Balance[]> {
  return authedGet<Balance[]>("/accounts", token);
}

export function fetchMyOpenOrders(token: string): Promise<OpenOrder[]> {
  return authedGet<OpenOrder[]>("/orders", token);
}
