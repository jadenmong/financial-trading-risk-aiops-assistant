import { z } from 'zod';

const booleanFromString = z.enum(['true', 'false']).transform((value) => value === 'true');

export const GatewayConfigSchema = z.object({
  nodeEnv: z.enum(['development', 'test', 'production']).default('development'),
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
  auditPath: z.string().min(1).default('runtime/gateway-audit.ndjson'),
});

export type GatewayConfig = z.infer<typeof GatewayConfigSchema>;

function csv(value: string | undefined, fallback: string[]): string[] {
  return value ? value.split(',').map((item) => item.trim()).filter(Boolean) : fallback;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): GatewayConfig {
  return GatewayConfigSchema.parse({
    nodeEnv: env.NODE_ENV,
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
    auditPath: env.AUDIT_PATH,
  });
}
