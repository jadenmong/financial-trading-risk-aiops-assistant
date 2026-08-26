import { createOidcState, createPkce } from './client.js';
import { useSessionStore } from '../stores/session.js';

const issuer = import.meta.env.VITE_OIDC_ISSUER ?? 'http://localhost:8081/realms/risk-aiops';
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID ?? 'ops-console';
const scopes = 'openid market:read risk:read reconciliation:read report:preview incident:read incident:write system:read evidence:read diagnosis:read diagnosis:write report:read report:write report:approve audit:read';

export async function beginLogin(): Promise<void> {
  const { verifier, challenge } = await createPkce();
  const state = createOidcState();
  sessionStorage.setItem('oidc.pkce.verifier', verifier);
  sessionStorage.setItem('oidc.state', state);
  const redirectUri = new URL('/auth/callback', window.location.origin).toString();
  const url = new URL(`${issuer}/protocol/openid-connect/auth`);
  url.search = new URLSearchParams({ response_type: 'code', client_id: clientId, redirect_uri: redirectUri, scope: scopes, state, code_challenge: challenge, code_challenge_method: 'S256' }).toString();
  window.location.assign(url);
}

export async function completeLogin(callbackUrl = window.location.href): Promise<void> {
  const url = new URL(callbackUrl);
  const code = url.searchParams.get('code');
  const state = url.searchParams.get('state');
  const expectedState = sessionStorage.getItem('oidc.state');
  const verifier = sessionStorage.getItem('oidc.pkce.verifier');
  sessionStorage.removeItem('oidc.state');
  sessionStorage.removeItem('oidc.pkce.verifier');
  if (!code || !state || state !== expectedState || !verifier) throw new Error('OIDC callback state or PKCE verifier is invalid');
  const redirectUri = new URL('/auth/callback', window.location.origin).toString();
  const response = await fetch(`${issuer}/protocol/openid-connect/token`, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'authorization_code', client_id: clientId, code, redirect_uri: redirectUri, code_verifier: verifier }) });
  if (!response.ok) throw new Error(`OIDC token exchange failed with ${response.status}`);
  const tokens = await response.json() as { access_token?: string; expires_in?: number };
  if (!tokens.access_token) throw new Error('OIDC response did not contain an access token');
  useSessionStore().establish(tokens.access_token, displaySubject(tokens.access_token));
  window.setTimeout(() => useSessionStore().clear(), Math.max(1, (tokens.expires_in ?? 300) - 5) * 1_000);
}

function displaySubject(token: string): string {
  try {
    const segment = token.split('.')[1];
    if (!segment) return 'authenticated-user';
    const payload = JSON.parse(atob(segment.replaceAll('-', '+').replaceAll('_', '/'))) as { preferred_username?: string; sub?: string };
    return payload.preferred_username ?? payload.sub ?? 'authenticated-user';
  } catch { return 'authenticated-user'; }
}
