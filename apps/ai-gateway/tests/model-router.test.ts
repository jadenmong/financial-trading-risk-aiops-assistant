import { describe, expect, it, vi } from 'vitest';

import { DeepSeekProvider, GovernedModelRouter, ProviderError, type ModelProvider } from '../src/model-provider.js';

const request = { system: 'verified evidence only', input: {}, safetyIdentifier: 'test-safe-id', reasoningEffort: 'low' as const };
function provider(name: string, implementation: ModelProvider['generate']): ModelProvider { return { name, model: `${name}-model`, generate: implementation }; }

describe('governed provider fallback', () => {
  it('falls back once after two eligible primary failures', async () => {
    const primary = provider('primary', vi.fn(async () => { throw new ProviderError('timeout', 'timeout'); }));
    const fallback = provider('fallback', vi.fn(async () => ({ summary: 'ok', evidenceIds: [], requiresReview: false })));
    const result = await new GovernedModelRouter(primary, fallback).generate(request);
    expect(result.provider).toBe('fallback');
    expect(primary.generate).toHaveBeenCalledTimes(2);
    expect(fallback.generate).toHaveBeenCalledTimes(1);
  });

  it.each(['auth', 'schema', 'refusal'] as const)('never bypasses %s failures by changing model', async (kind) => {
    const primary = provider('primary', vi.fn(async () => { throw new ProviderError(kind, kind); }));
    const fallback = provider('fallback', vi.fn(async () => ({ summary: 'should not run', evidenceIds: [], requiresReview: false })));
    await expect(new GovernedModelRouter(primary, fallback).generate(request)).rejects.toMatchObject({ kind });
    expect(fallback.generate).not.toHaveBeenCalled();
  });
});

describe('DeepSeek provider', () => {
  it('uses the configured OpenAI-compatible endpoint and parses JSON output', async () => {
    const requests: Array<{ url: string; init?: RequestInit }> = [];
    vi.stubGlobal('fetch', async (input: string | URL | Request, init?: RequestInit) => {
      requests.push({ url: String(input), ...(init ? { init } : {}) });
      return new Response(JSON.stringify({ choices: [{ message: { content: JSON.stringify({ summary: '已核对', evidenceIds: ['ev-1'], requiresReview: false }) } }] }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      });
    });

    const output = await new DeepSeekProvider('deepseek-v4-pro', 'test-secret', 'https://api.deepseek.com').generate({
      system: 'verified evidence only',
      input: { evidenceId: 'ev-1' },
      safetyIdentifier: 'test-safe-id',
      reasoningEffort: 'low',
    });

    expect(output).toEqual({ summary: '已核对', evidenceIds: ['ev-1'], requiresReview: false });
    expect(requests[0]?.url).toBe('https://api.deepseek.com/chat/completions');
    expect(new Headers(requests[0]?.init?.headers).get('authorization')).toBe('Bearer test-secret');
    const body = JSON.parse(String(requests[0]?.init?.body)) as Record<string, unknown>;
    expect(body).toMatchObject({ model: 'deepseek-v4-pro', response_format: { type: 'json_object' }, thinking: { type: 'disabled' }, stream: false });
    vi.unstubAllGlobals();
  });

  it('fails closed when the API key is absent', async () => {
    await expect(new DeepSeekProvider('deepseek-v4-pro', '', 'https://api.deepseek.com').generate(request)).rejects.toMatchObject({ kind: 'auth' });
  });
});
