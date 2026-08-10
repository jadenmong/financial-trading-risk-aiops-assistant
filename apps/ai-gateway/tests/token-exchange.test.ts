import { afterEach, describe, expect, it, vi } from 'vitest';

import { KeycloakTokenExchange } from '../src/token-exchange.js';

afterEach(() => vi.unstubAllGlobals());

describe('Keycloak standard token exchange', () => {
  it('requests a down-scoped Risk Core token and never returns the user token', async () => {
    let form = new URLSearchParams();
    vi.stubGlobal('fetch', vi.fn(async (_url: string, init: RequestInit) => {
      form = new URLSearchParams(String(init.body));
      return new Response(JSON.stringify({ access_token: 'down-scoped-core-token', expires_in: 60, token_type: 'Bearer' }), { status: 200, headers: { 'content-type': 'application/json' } });
    }));
    const exchange = new KeycloakTokenExchange('https://identity.example/token', 'ai-gateway', 'client-secret', 'risk-core');
    const result = await exchange.exchange('original-user-token', ['risk:read']);
    expect(result).toBe('down-scoped-core-token');
    expect(form.get('subject_token')).toBe('original-user-token');
    expect(form.get('audience')).toBe('risk-core');
    expect(form.get('scope')).toBe('risk:read');
    expect(form.get('grant_type')).toBe('urn:ietf:params:oauth:grant-type:token-exchange');
  });
});
