import { createRemoteJWKSet, jwtVerify } from 'jose';
import type { AuthInfo } from '@modelcontextprotocol/server';

import type { GatewayConfig } from './config.js';

export interface Authenticator { authenticate(header: string | undefined): Promise<AuthInfo>; }

export class AuthenticationError extends Error {
  constructor(readonly code: 'invalid_token' | 'insufficient_scope', message: string) { super(message); }
}

export class JwtAuthenticator implements Authenticator {
  private readonly jwks;
  constructor(private readonly config: GatewayConfig) {
    this.jwks = createRemoteJWKSet(new URL(`${config.issuer}/protocol/openid-connect/certs`));
  }

  async authenticate(header: string | undefined): Promise<AuthInfo> {
    const token = extractBearer(header);
    const { payload } = await jwtVerify(token, this.jwks, { issuer: this.config.issuer, audience: this.config.audience });
    if (!payload.sub || !payload.exp) throw new AuthenticationError('invalid_token', 'token requires sub and exp');
    const scopes = typeof payload.scope === 'string' ? payload.scope.split(' ').filter(Boolean) : [];
    return {
      token,
      clientId: typeof payload.azp === 'string' ? payload.azp : 'unknown-client',
      scopes,
      expiresAt: payload.exp,
      resource: new URL(this.config.resourceUrl),
      extra: {
        subject: payload.sub,
        desks: arrayClaim(payload.desks),
        portfolios: arrayClaim(payload.portfolios),
        accounts: arrayClaim(payload.accounts),
        roles: arrayClaim(payload.roles),
      },
    };
  }
}

export class ReferenceAuthenticator implements Authenticator {
  async authenticate(header: string | undefined): Promise<AuthInfo> {
    const token = extractBearer(header);
    if (token !== 'reference-token') throw new AuthenticationError('invalid_token', 'invalid reference token');
    return {
      token,
      clientId: 'reference-console',
      scopes: ['market:read', 'risk:read', 'reconciliation:read', 'report:preview', 'diagnosis:write', 'diagnosis:read', 'report:write', 'report:read', 'report:approve', 'audit:read'],
      expiresAt: Math.floor(Date.now() / 1000) + 300,
      extra: { subject: 'reference-analyst', desks: ['DESK_ALPHA'], accounts: ['ACC_ALPHA_01', 'ACC_ALPHA_02'], roles: ['risk-analyst'] },
    };
  }
}

function extractBearer(header: string | undefined): string {
  if (!header?.startsWith('Bearer ')) throw new AuthenticationError('invalid_token', 'Bearer token required');
  const token = header.slice(7).trim();
  if (!token) throw new AuthenticationError('invalid_token', 'Bearer token required');
  return token;
}

function arrayClaim(value: unknown): string[] { return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []; }

export function subjectOf(authInfo: AuthInfo | undefined): string {
  const subject = authInfo?.extra?.subject;
  return typeof subject === 'string' ? subject : 'local-stdio';
}
