import { useSessionStore } from '../stores/session.js';

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const session = useSessionStore();
  const response = await fetch(path, { ...init, headers: { 'content-type': 'application/json', ...(session.accessToken ? { authorization: `Bearer ${session.accessToken}` } : {}), ...init.headers } });
  if (!response.ok) throw new Error(`API ${response.status}`);
  return response.json() as Promise<T>;
}

export async function createPkce(): Promise<{ verifier: string; challenge: string }> {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  const verifier = base64url(bytes);
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return { verifier, challenge: base64url(new Uint8Array(digest)) };
}
function base64url(bytes: Uint8Array): string { return btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', ''); }
