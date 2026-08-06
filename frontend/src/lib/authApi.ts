export interface AuthSession {
  token: string;
  userId: string;
  email: string;
  expiresAt: number; // epoch ms
}

const STORAGE_KEY = "openex.session";

export interface AuthError {
  error: string;
  message: string | null;
}

async function callAuthEndpoint(path: "/auth/register" | "/auth/login", email: string, password: string): Promise<AuthSession> {
  const res = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });

  const body = await res.json();

  if (!res.ok) {
    const err = body as AuthError;
    throw new Error(err.message ?? err.error ?? "Request failed");
  }

  const session: AuthSession = {
    token: body.token,
    userId: body.userId,
    email: body.email,
    expiresAt: Date.now() + body.expiresInSeconds * 1000,
  };

  saveSession(session);
  return session;
}

export function register(email: string, password: string): Promise<AuthSession> {
  return callAuthEndpoint("/auth/register", email, password);
}

export function login(email: string, password: string): Promise<AuthSession> {
  return callAuthEndpoint("/auth/login", email, password);
}

export function saveSession(session: AuthSession): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function loadSession(): AuthSession | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;

  try {
    const session = JSON.parse(raw) as AuthSession;
    if (session.expiresAt <= Date.now()) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return session;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function clearSession(): void {
  localStorage.removeItem(STORAGE_KEY);
}
