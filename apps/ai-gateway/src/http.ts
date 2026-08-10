import './instrumentation.js';

import { Readable } from 'node:stream';
import { createMcpHandler, type AuthInfo } from '@modelcontextprotocol/server';
import express, { type Request as ExpressRequest, type Response as ExpressResponse } from 'express';
import { rateLimit } from 'express-rate-limit';
import pino from 'pino';

import { NdjsonAuditSink } from './audit.js';
import { JwtAuthenticator, ReferenceAuthenticator, type Authenticator } from './auth.js';
import { loadConfig, productionViolations, type GatewayConfig } from './config.js';
import { validateHostAndOrigin } from './http-security.js';
import { HttpRiskCoreClient, SampleRiskCoreClient, type RiskCoreClient } from './risk-core-client.js';
import { createFinancialRiskMcpServer } from './server.js';
import { KeycloakTokenExchange } from './token-exchange.js';

const logger = pino({ name: 'ai-gateway', level: process.env.LOG_LEVEL ?? 'info' });

export interface AppDependencies {
  config?: GatewayConfig;
  authenticator?: Authenticator;
  core?: RiskCoreClient;
}

export function createApp(dependencies: AppDependencies = {}) {
  const config = dependencies.config ?? loadConfig();
  const authenticator = dependencies.authenticator ?? (config.referenceAuth ? new ReferenceAuthenticator() : new JwtAuthenticator(config));
  const core = dependencies.core ?? (config.riskCoreMode === 'sample' ? new SampleRiskCoreClient() : new HttpRiskCoreClient(config.riskCoreUrl));
  const tokenExchange = config.referenceAuth ? undefined : createTokenExchange(config);
  const audit = new NdjsonAuditSink(config.auditPath);
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '256kb', strict: true }));
  app.get('/health/live', (_request, response) => response.json({ status: 'UP' }));
  app.get('/health/ready', (_request, response) => {
    const violations = productionViolations(config);
    if (violations.length > 0) {
      response.status(503).json({ status: 'DOWN', mode: config.runtimeMode, violations });
      return;
    }
    response.json({ status: 'UP', mode: config.runtimeMode, dependencies: { riskCore: 'configured' } });
  });
  app.get('/.well-known/oauth-protected-resource', (_request, response) => response.json({
    resource: config.resourceUrl,
    authorization_servers: [config.issuer],
    scopes_supported: ['market:read', 'risk:read', 'reconciliation:read', 'report:preview', 'incident:read', 'system:read', 'audit:read'],
    bearer_methods_supported: ['header'],
  }));

  const handler = createMcpHandler((context) => createFinancialRiskMcpServer({ ...(context.authInfo ? { authInfo: context.authInfo } : {}), core, audit, ...(tokenExchange ? { tokenExchange } : {}) }), {
    legacy: 'stateless', responseMode: 'auto', onerror: (error) => logger.error({ err: error }, 'MCP handler error'),
  });
  const mcpRateLimiter = rateLimit({
    windowMs: config.rateLimitWindowMs,
    limit: config.rateLimitMax,
    standardHeaders: 'draft-8',
    legacyHeaders: false,
    passOnStoreError: false,
    handler: (_request, response) => response.status(429).json({ error: 'rate_limited' }),
  });
  app.all('/mcp', validateHostAndOrigin(config.allowedHosts, config.allowedOrigins), mcpRateLimiter, async (request, response) => {
    let authInfo: AuthInfo;
    try {
      authInfo = await authenticator.authenticate(request.get('authorization'));
    } catch {
      response.set('WWW-Authenticate', `Bearer resource_metadata="${new URL('/.well-known/oauth-protected-resource', config.resourceUrl)}"`);
      response.status(401).json({ error: 'invalid_token' });
      return;
    }
    const webRequest = toWebRequest(request);
    const webResponse = await handler.fetch(webRequest, { authInfo, parsedBody: request.body });
    await sendWebResponse(webResponse, response);
  });
  return app;
}

function createTokenExchange(config: GatewayConfig): KeycloakTokenExchange {
  if (!config.gatewayClientSecret) throw new Error('GATEWAY_CLIENT_SECRET is required outside reference mode');
  return new KeycloakTokenExchange(
    config.tokenEndpoint ?? `${config.issuer}/protocol/openid-connect/token`,
    config.gatewayClientId,
    config.gatewayClientSecret,
    config.riskCoreAudience,
  );
}

function toWebRequest(request: ExpressRequest): globalThis.Request {
  const headers = new Headers();
  for (const [name, value] of Object.entries(request.headers)) {
    if (Array.isArray(value)) value.forEach((item) => headers.append(name, item));
    else if (value !== undefined) headers.set(name, value);
  }
  const url = `${request.protocol}://${request.get('host')}${request.originalUrl}`;
  const hasBody = !['GET', 'HEAD'].includes(request.method);
  return new globalThis.Request(url, { method: request.method, headers, ...(hasBody ? { body: JSON.stringify(request.body) } : {}) });
}

async function sendWebResponse(webResponse: globalThis.Response, response: ExpressResponse): Promise<void> {
  response.status(webResponse.status);
  webResponse.headers.forEach((value, name) => response.setHeader(name, value));
  if (!webResponse.body) { response.end(); return; }
  await new Promise<void>((resolve, reject) => {
    Readable.fromWeb(webResponse.body as never).on('error', reject).on('end', resolve).pipe(response);
  });
}

if (process.env.NODE_ENV !== 'test') {
  const config = loadConfig();
  const app = createApp({ config });
  const server = app.listen(config.port, () => logger.info({ port: config.port }, 'AI/MCP Gateway listening'));
  const shutdown = () => server.close(() => process.exit(0));
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}
