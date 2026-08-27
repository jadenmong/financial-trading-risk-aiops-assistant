import { afterEach, describe, expect, it, vi } from 'vitest';
import { loginUrl, logoutUrl } from './auth.js';

afterEach(() => vi.unstubAllGlobals());

describe('OIDC navigation URLs', () => {
  it('forces credential entry when starting a login', () => {
    vi.stubGlobal('window', { location: { origin: 'http://192.168.1.88:5173' } });

    const url = new URL(loginUrl('challenge', 'state'));

    expect(url.searchParams.get('prompt')).toBe('login');
    expect(url.searchParams.get('redirect_uri')).toBe('http://192.168.1.88:5173/auth/callback');
  });

  it('ends the Keycloak session and returns to the console', () => {
    vi.stubGlobal('window', { location: { origin: 'http://192.168.1.88:5173' } });

    const url = new URL(logoutUrl('id-token'));

    expect(url.pathname).toBe('/realms/risk-aiops/protocol/openid-connect/logout');
    expect(url.searchParams.get('id_token_hint')).toBe('id-token');
    expect(url.searchParams.get('client_id')).toBe('ops-console');
    expect(url.searchParams.get('post_logout_redirect_uri')).toBe('http://192.168.1.88:5173/');
  });
});
