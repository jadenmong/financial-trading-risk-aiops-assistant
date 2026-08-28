import { z } from 'zod';

const booleanFromString = z.enum(['true', 'false']).transform((value) => value === 'true');

export const GatewayConfigSchema = z.object({
  nodeEnv: z.enum(['development', 'test', 'production']).default('development'),
  runtimeMode: z.enum(['reference', 'staging', 'production']).default('reference'),
  port: z.coerce.number().int().min(1).max(65535).default(3000),
  riskCoreUrl: z.url().default('http://localhost:8080'),
  issuer: z.url().default('http://localhost:8081/realms/risk-aiops'),
  audience: z.string().min(1).default('risk-aiops-mcp'),
  riskCoreAudience: z.string().min(1).default('risk-core'),
  gatewayClientId: z.string().min(1).default('ai-gateway'),
  gatewayClientSecret: z.string().min(1).optional(),
  tokenEndpoint: z.url().optional(),
  resourceUrl: z.url().default('http://localhost:3000/mcp'),
  allowedHosts: z.array(z.string().min(1)).min(1),
  allowedOrigins: z.array(z.string().min(1)).min(1),
  referenceAuth: z.boolean().default(false),
  modelProvider: z.enum(['fake', 'deepseek']).default('fake'),
  deepseekBaseUrl: z.url().default('https://api.deepseek.com'),
  deepseekModel: z.string().min(1).default('deepseek-v4-pro'),
  deepseekApiKey: z.string().min(1).optional(),
  riskCoreMode: z.string().min(1).default('http'),
  auditPath: z.string().min(1).default('runtime/gateway-audit.ndjson'),
  rateLimitWindowMs: z.coerce.number().int().min(1_000).max(3_600_000).default(60_000),
  rateLimitMax: z.coerce.number().int().min(1).max(100_000).default(120),
});

export type GatewayConfig = z.infer<typeof GatewayConfigSchema>;

function csv(value: string | undefined, fallback: string[]): string[] {
  return value ? value.split(',').map((item) => item.trim()).filter(Boolean) : fallback;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): GatewayConfig {
  return GatewayConfigSchema.parse({
    nodeEnv: env.NODE_ENV,
    runtimeMode: env.APP_RUNTIME_MODE,
    port: env.GATEWAY_PORT,
    riskCoreUrl: env.RISK_CORE_URL,
    issuer: env.OIDC_ISSUER,
    audience: env.MCP_AUDIENCE,
    riskCoreAudience: env.RISK_CORE_AUDIENCE,
    gatewayClientId: env.GATEWAY_CLIENT_ID,
    gatewayClientSecret: env.GATEWAY_CLIENT_SECRET,
    tokenEndpoint: env.OIDC_TOKEN_ENDPOINT,
    resourceUrl: env.MCP_RESOURCE_URL,
    allowedHosts: csv(env.MCP_ALLOWED_HOSTS, ['localhost', '127.0.0.1']),
    allowedOrigins: csv(env.MCP_ALLOWED_ORIGINS, ['http://localhost:5173']),
    referenceAuth: booleanFromString.parse(env.REFERENCE_AUTH ?? 'false'),
    modelProvider: env.MODEL_PROVIDER,
    deepseekBaseUrl: env.DEEPSEEK_BASE_URL,
    deepseekModel: env.DEEPSEEK_MODEL,
    deepseekApiKey: env.DEEPSEEK_API_KEY,
    riskCoreMode: env.RISK_CORE_MODE,
    auditPath: env.AUDIT_PATH,
    rateLimitWindowMs: env.MCP_RATE_LIMIT_WINDOW_MS,
    rateLimitMax: env.MCP_RATE_LIMIT_MAX,
  });
}

export function productionViolations(config: GatewayConfig): string[] {
  if (config.runtimeMode !== 'production') return [];
  const violations: string[] = [];
  if (config.referenceAuth) violations.push('REFERENCE_AUTH must be false');
  if (config.riskCoreMode === 'sample') violations.push('RISK_CORE_MODE=sample is forbidden');
  if (config.modelProvider === 'fake') violations.push('MODEL_PROVIDER=fake is forbidden');
  if (config.modelProvider === 'deepseek' && !config.deepseekApiKey) violations.push('DEEPSEEK_API_KEY is required');
  if (config.modelProvider === 'deepseek' && new URL(config.deepseekBaseUrl).protocol !== 'https:') violations.push('DEEPSEEK_BASE_URL must use HTTPS');
  if (!config.gatewayClientSecret) violations.push('GATEWAY_CLIENT_SECRET is required');
  if (config.issuer.includes('localhost') || config.issuer.includes('127.0.0.1')) violations.push('OIDC issuer must not be localhost');
  if (config.auditPath.startsWith('runtime/')) violations.push('AUDIT_PATH must point at managed persistent storage');
  if (config.allowedHosts.some((host) => ['localhost', '127.0.0.1'].includes(host))) violations.push('localhost allowed host is forbidden');
  if (config.allowedOrigins.some((origin) => origin.includes('localhost') || origin.includes('127.0.0.1'))) violations.push('localhost allowed origin is forbidden');
  return violations;
}
