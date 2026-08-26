import { sha256 } from '@noble/hashes/sha2.js';
import { useSessionStore } from '../stores/session.js';

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const session = useSessionStore();
  const response = await fetch(path, { ...init, headers: { 'content-type': 'application/json', ...(session.accessToken ? { authorization: `Bearer ${session.accessToken}` } : {}), ...init.headers } });
  if (!response.ok) throw new Error(`API ${response.status}`);
  return response.json() as Promise<T>;
}

export async function createPkce(): Promise<{ verifier: string; challenge: string }> {
  const verifier = createOidcState();
  return { verifier, challenge: createPkceChallenge(verifier) };
}

export function createPkceChallenge(verifier: string): string {
  return base64url(sha256(new TextEncoder().encode(verifier)));
}

export function createOidcState(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return base64url(bytes);
}

function base64url(bytes: Uint8Array): string { return btoa(String.fromCharCode(...bytes)).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', ''); }
