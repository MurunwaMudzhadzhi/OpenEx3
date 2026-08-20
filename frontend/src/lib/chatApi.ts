export interface ChatResponse {
  reply: string;
}

export function sendChatMessage(message: string, token?: string): Promise<ChatResponse> {
  return fetch("/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message }),
  }).then((res) => {
    if (!res.ok) {
      throw new Error(`Request to /chat failed (${res.status})`);
    }
    return res.json() as Promise<ChatResponse>;
  });
}
