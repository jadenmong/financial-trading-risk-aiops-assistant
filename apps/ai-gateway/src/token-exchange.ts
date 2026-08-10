import { z } from 'zod';

const TokenResponseSchema = z.object({
  access_token: z.string().min(1),
  expires_in: z.number().int().positive().optional(),
  token_type: z.string().optional(),
});

export class TokenExchangeError extends Error {}

export interface TokenExchange {
  exchange(subjectToken: string, scopes: string[]): Promise<string>;
}

export class KeycloakTokenExchange implements TokenExchange {
  constructor(
    private readonly tokenEndpoint: string,
    private readonly clientId: string,
    private readonly clientSecret: string,
    private readonly audience = 'risk-core',
  ) {}

  async exchange(subjectToken: string, scopes: string[]): Promise<string> {
    const body = new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:token-exchange',
      subject_token: subjectToken,
      subject_token_type: 'urn:ietf:params:oauth:token-type:access_token',
      requested_token_type: 'urn:ietf:params:oauth:token-type:access_token',
      audience: this.audience,
      scope: scopes.join(' '),
      client_id: this.clientId,
      client_secret: this.clientSecret,
    });
    const response = await fetch(this.tokenEndpoint, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' }, body, signal: AbortSignal.timeout(3_000) });
    if (!response.ok) throw new TokenExchangeError(`token exchange failed with ${response.status}`);
    const parsed = TokenResponseSchema.safeParse(await response.json());
    if (!parsed.success) throw new TokenExchangeError('token exchange returned an invalid response');
    return parsed.data.access_token;
  }
}
