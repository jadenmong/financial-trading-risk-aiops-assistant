import { describe, expect, it, vi } from 'vitest';

import { GovernedModelRouter, ProviderError, type ModelProvider } from '../src/model-provider.js';

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
