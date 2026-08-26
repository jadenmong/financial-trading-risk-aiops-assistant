import { afterEach, describe, expect, it, vi } from 'vitest';
import { createOidcState, createPkce, createPkceChallenge } from './client.js';

afterEach(() => vi.unstubAllGlobals());

describe('OIDC PKCE helpers', () => {
  it('generates the RFC 7636 S256 challenge', () => {
    expect(createPkceChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'))
      .toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
  });

  it('works when only getRandomValues is available on an HTTP origin', async () => {
    vi.stubGlobal('crypto', {
      getRandomValues(bytes: Uint8Array) {
        bytes.fill(7);
        return bytes;
      },
    });

    const state = createOidcState();
    const pkce = await createPkce();

    expect(state).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(pkce.verifier).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(pkce.challenge).toBe(createPkceChallenge(pkce.verifier));
  });
});
