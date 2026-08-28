import request from 'supertest';
import { describe, expect, it } from 'vitest';

import { GatewayConfigSchema, productionViolations } from '../src/config.js';
import { createApp } from '../src/http.js';

const config = GatewayConfigSchema.parse({ allowedHosts: ['localhost'], allowedOrigins: ['http://localhost:5173'], referenceAuth: true });
const app = createApp({ config });

describe('HTTP transport edge controls', () => {
  it('publishes OAuth protected resource metadata', async () => {
    const response = await request(app).get('/.well-known/oauth-protected-resource');
    expect(response.status).toBe(200);
    expect(response.body.scopes_supported).toContain('market:read');
    expect(response.body.scopes_supported).toContain('incident:read');
  });

  it('fails readiness when production still uses reference paths', async () => {
    const unsafeConfig = GatewayConfigSchema.parse({
      runtimeMode: 'production',
      allowedHosts: ['localhost'],
      allowedOrigins: ['http://localhost:5173'],
      referenceAuth: true,
      riskCoreMode: 'sample',
      modelProvider: 'fake',
    });
    const response = await request(createApp({ config: unsafeConfig })).get('/health/ready');
    expect(response.status).toBe(503);
    expect(response.body.violations).toContain('REFERENCE_AUTH must be false');
  });

  it('requires a DeepSeek key and HTTPS endpoint in production', () => {
    const unsafeModelConfig = GatewayConfigSchema.parse({
      runtimeMode: 'production',
      allowedHosts: ['gateway.example.com'],
      allowedOrigins: ['https://console.example.com'],
      referenceAuth: false,
      riskCoreMode: 'http',
      modelProvider: 'deepseek',
      deepseekBaseUrl: 'http://api.deepseek.com',
      issuer: 'https://identity.example.com/realms/risk-aiops',
      gatewayClientSecret: 'test-only',
      auditPath: '/managed/audit.ndjson',
    });
    expect(productionViolations(unsafeModelConfig)).toEqual(expect.arrayContaining(['DEEPSEEK_API_KEY is required', 'DEEPSEEK_BASE_URL must use HTTPS']));
  });

  it('rejects invalid Host and Origin before MCP handling', async () => {
    expect((await request(app).post('/mcp').set('Host', 'evil.example').send({})).status).toBe(403);
    expect((await request(app).post('/mcp').set('Host', 'localhost').set('Origin', 'https://evil.example').send({})).status).toBe(403);
  });

  it('returns a resource metadata challenge when token is absent', async () => {
    const response = await request(app).post('/mcp').set('Host', 'localhost').send({});
    expect(response.status).toBe(401);
    expect(response.headers['www-authenticate']).toContain('resource_metadata');
  });

  it('rate limits repeated requests to the protected MCP endpoint', async () => {
    const limitedConfig = GatewayConfigSchema.parse({
      allowedHosts: ['localhost'],
      allowedOrigins: ['http://localhost:5173'],
      referenceAuth: true,
      rateLimitMax: 2,
      rateLimitWindowMs: 60_000,
    });
    const limitedApp = createApp({ config: limitedConfig });
    expect((await request(limitedApp).post('/mcp').set('Host', 'localhost').send({})).status).toBe(401);
    expect((await request(limitedApp).post('/mcp').set('Host', 'localhost').send({})).status).toBe(401);
    expect((await request(limitedApp).post('/mcp').set('Host', 'localhost').send({})).status).toBe(429);
  });
});
