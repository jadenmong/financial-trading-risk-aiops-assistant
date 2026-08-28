import { createHash } from 'node:crypto';
import { z } from 'zod';

import type { GatewayConfig } from './config.js';

const ModelOutputSchema = z.object({
  summary: z.string().min(1).max(4_000),
  evidenceIds: z.array(z.string()).max(100),
  requiresReview: z.boolean(),
});
export type ModelOutput = z.infer<typeof ModelOutputSchema>;

export interface ModelRequest {
  system: string;
  input: Record<string, unknown>;
  safetyIdentifier: string;
  reasoningEffort: 'low' | 'medium';
}

export interface ModelProvider {
  readonly name: string;
  readonly model: string;
  generate(request: ModelRequest): Promise<ModelOutput>;
}

export class ProviderError extends Error {
  constructor(readonly kind: 'timeout' | 'rate_limit' | 'server' | 'circuit_open' | 'refusal' | 'schema' | 'auth', message: string) { super(message); }
}

export class FakeModelProvider implements ModelProvider {
  readonly name = 'fake';
  readonly model = 'deterministic-fake-v1';
  async generate(request: ModelRequest): Promise<ModelOutput> {
    const evidenceIds = findEvidenceIds(request.input);
    return { summary: `Deterministic diagnosis ${createHash('sha256').update(JSON.stringify(request.input)).digest('hex').slice(0, 12)}`, evidenceIds, requiresReview: false };
  }
}

export class DeepSeekProvider implements ModelProvider {
  readonly name = 'deepseek';

  constructor(
    readonly model = 'deepseek-v4-pro',
    private readonly apiKey = process.env.DEEPSEEK_API_KEY ?? '',
    private readonly baseUrl = process.env.DEEPSEEK_BASE_URL ?? 'https://api.deepseek.com',
  ) {}

  async generate(request: ModelRequest): Promise<ModelOutput> {
    if (!this.apiKey) throw new ProviderError('auth', 'DeepSeek API key is not configured');
    const endpoint = new URL('chat/completions', ensureTrailingSlash(this.baseUrl));
    const thinkingEnabled = request.reasoningEffort === 'medium';
    const response = await providerFetch(endpoint.toString(), {
      method: 'POST',
      headers: { authorization: `Bearer ${this.apiKey}`, 'content-type': 'application/json' },
      body: JSON.stringify({
        model: this.model,
        messages: [
          { role: 'system', content: `${request.system}\nReturn one JSON object matching this schema exactly: ${JSON.stringify(modelJsonSchema)}` },
          { role: 'user', content: JSON.stringify(request.input) },
        ],
        response_format: { type: 'json_object' },
        thinking: { type: thinkingEnabled ? 'enabled' : 'disabled' },
        ...(thinkingEnabled ? { reasoning_effort: request.reasoningEffort } : {}),
        max_tokens: 1_200,
        stream: false,
      }),
      signal: AbortSignal.timeout(8_000),
    });
    if (response.status === 429) throw new ProviderError('rate_limit', 'DeepSeek rate limited');
    if (response.status >= 500) throw new ProviderError('server', `DeepSeek returned ${response.status}`);
    if ([401, 402, 403].includes(response.status)) throw new ProviderError('auth', 'DeepSeek authentication or account balance check failed');
    if (!response.ok) throw new ProviderError('refusal', `DeepSeek request rejected with ${response.status}`);
    const body = await response.json() as { choices?: Array<{ message?: { content?: string } }> };
    return parseOutput(body.choices?.[0]?.message?.content);
  }
}

export function createConfiguredModelProvider(config: GatewayConfig): ModelProvider {
  if (config.modelProvider === 'deepseek') return new DeepSeekProvider(config.deepseekModel, config.deepseekApiKey, config.deepseekBaseUrl);
  return new FakeModelProvider();
}

export class GovernedModelRouter {
  private consecutivePrimaryFailures = 0;
  private circuitOpenedAt = 0;
  constructor(private readonly primary: ModelProvider, private readonly fallback: ModelProvider, private readonly circuitResetMs = 30_000) {}

  async generate(request: ModelRequest): Promise<{ output: ModelOutput; provider: string; model: string }> {
    const circuitOpen = this.consecutivePrimaryFailures >= 3 && Date.now() - this.circuitOpenedAt < this.circuitResetMs;
    if (circuitOpen) return this.callFallback(request, new ProviderError('circuit_open', 'primary circuit open'));
    let lastError: unknown;
    for (let attempt = 0; attempt < 2; attempt += 1) {
      try {
        const output = await this.primary.generate(request);
        this.consecutivePrimaryFailures = 0;
        return { output, provider: this.primary.name, model: this.primary.model };
      } catch (error) {
        lastError = error;
        if (!isFallbackEligible(error)) throw error;
        this.consecutivePrimaryFailures += 1;
        if (this.consecutivePrimaryFailures >= 3) this.circuitOpenedAt = Date.now();
      }
    }
    return this.callFallback(request, lastError);
  }

  private async callFallback(request: ModelRequest, cause: unknown) {
    if (!isFallbackEligible(cause)) throw cause;
    const output = await this.fallback.generate(request);
    return { output, provider: this.fallback.name, model: this.fallback.model };
  }
}

function isFallbackEligible(error: unknown): boolean {
  return error instanceof ProviderError && ['timeout', 'rate_limit', 'server', 'circuit_open'].includes(error.kind);
}

async function providerFetch(url: string, init: RequestInit): Promise<Response> {
  try { return await fetch(url, init); }
  catch (error) {
    if (error instanceof Error && (error.name === 'TimeoutError' || error.name === 'AbortError')) throw new ProviderError('timeout', 'model provider timed out');
    throw new ProviderError('server', 'model provider transport failed');
  }
}

function parseOutput(value: string | undefined): ModelOutput {
  if (!value) throw new ProviderError('schema', 'model returned no structured output');
  try { return ModelOutputSchema.parse(JSON.parse(value)); } catch { throw new ProviderError('schema', 'model output violated schema'); }
}

function ensureTrailingSlash(value: string): string { return value.endsWith('/') ? value : `${value}/`; }

function findEvidenceIds(value: unknown): string[] {
  const ids = new Set<string>();
  const visit = (item: unknown) => {
    if (Array.isArray(item)) item.forEach(visit);
    else if (item && typeof item === 'object') for (const [key, child] of Object.entries(item)) { if (key === 'evidenceId' && typeof child === 'string') ids.add(child); visit(child); }
  };
  visit(value);
  return [...ids].sort();
}

const modelJsonSchema = {
  type: 'object', additionalProperties: false, required: ['summary', 'evidenceIds', 'requiresReview'],
  properties: { summary: { type: 'string' }, evidenceIds: { type: 'array', items: { type: 'string' } }, requiresReview: { type: 'boolean' } },
};
