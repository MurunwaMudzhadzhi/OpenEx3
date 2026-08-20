export interface MarketTick {
  timestamp: string;
  price: number;
  moving_average: number;
}

export interface MarketDataResponse {
  symbol: string;
  current: MarketTick;
  history: MarketTick[];
}

export function fetchMarketData(): Promise<MarketDataResponse> {
  return fetch("/api/market-data").then((res) => {
    if (!res.ok) {
      throw new Error(`Request to /api/market-data failed (${res.status})`);
    }
    return res.json() as Promise<MarketDataResponse>;
  });
}
