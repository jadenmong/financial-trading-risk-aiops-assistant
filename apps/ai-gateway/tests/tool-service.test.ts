import type { AuthInfo } from '@modelcontextprotocol/server';
import { describe, expect, it } from 'vitest';

import { FailingAuditSink, InMemoryAuditSink } from '../src/audit.js';
import { SampleRiskCoreClient } from '../src/risk-core-client.js';
import { ToolService } from '../src/tool-service.js';
import type { ModelProvider } from '../src/model-provider.js';

const auth: AuthInfo = {
  token: 'opaque-user-token', clientId: 'test-client', scopes: ['market:read', 'risk:read'], expiresAt: 2_000_000_000,
  extra: { subject: 'analyst-a', accounts: ['ACC_ALPHA_01'] },
};

describe('ToolService fail-closed boundary', () => {
  it('returns deterministic data and appends a hashed audit record', async () => {
    const audit = new InMemoryAuditSink();
    const result = await new ToolService(new SampleRiskCoreClient(), audit).execute('get_position_risk', { accountId: 'ACC_ALPHA_01' }, auth);
    expect(result.ok).toBe(true);
    expect(result.data?.grossExposure).toBe('190305000.0000000000');
    expect(result.meta.evidenceRefs[0]?.sha256).toMatch(/^[0-9a-f]{64}$/);
    expect(audit.records).toHaveLength(1);
    expect(audit.records[0]?.inputHash).toMatch(/^[0-9a-f]{64}$/);
  });

  it('audits domain errors without exposing stacks', async () => {
    const audit = new InMemoryAuditSink();
    const result = await new ToolService(new SampleRiskCoreClient(), audit).execute('get_market_snapshot', { instrumentId: 'SSE:999999' }, auth);
    expect(result).toMatchObject({ ok: false, error: { code: 'SNAPSHOT_NOT_FOUND' } });
    expect(JSON.stringify(result)).not.toContain('stack');
    expect(audit.records).toHaveLength(1);
  });

  it('blocks successful data when audit persistence fails', async () => {
    const result = await new ToolService(new SampleRiskCoreClient(), new FailingAuditSink()).execute('get_market_snapshot', { instrumentId: 'SSE:600519' }, auth);
    expect(result).toMatchObject({ ok: false, error: { code: 'AUDIT_WRITE_FAILED' }, meta: { qualityStatus: 'REJECTED' } });
    expect(result.data).toBeUndefined();
  });

  it('denies cross-account ABAC access before calling the core', async () => {
    const audit = new InMemoryAuditSink();
    const result = await new ToolService(new SampleRiskCoreClient(), audit).execute('get_position_risk', { accountId: 'ACC_BETA_01' }, auth);
    expect(result).toMatchObject({ ok: false, error: { code: 'ACCESS_DENIED' } });
    expect(audit.records[0]?.outcome).toBe('error');
  });

  it('uses the model only for explanation tools and records model metadata', async () => {
    const model: ModelProvider = {
      name: 'deepseek', model: 'deepseek-v4-pro',
      async generate() { return { summary: '需要人工复核两项关键差异。', evidenceIds: [], requiresReview: true }; },
    };
    const result = await new ToolService(new SampleRiskCoreClient(), new InMemoryAuditSink(), undefined, model)
      .execute('generate_daily_report', { accountId: 'ACC_ALPHA_01', tradeDate: '2026-08-07' });
    expect(result).toMatchObject({
      ok: true,
      data: { aiSummary: '需要人工复核两项关键差异。', requiresReview: true },
      meta: { modelVersion: 'deepseek/deepseek-v4-pro', promptVersion: 'report-synthesis-v1' },
    });
  });

  it('rejects model evidence ids that were not supplied by Risk Core', async () => {
    const model: ModelProvider = {
      name: 'deepseek', model: 'deepseek-v4-pro',
      async generate() { return { summary: 'invalid', evidenceIds: ['ev-not-supplied'], requiresReview: false }; },
    };
    const result = await new ToolService(new SampleRiskCoreClient(), new InMemoryAuditSink(), undefined, model)
      .execute('explain_reconciliation_breaks', { accountId: 'ACC_ALPHA_01', tradeDate: '2026-08-07' });
    expect(result).toMatchObject({ ok: false, error: { code: 'MODEL_SCHEMA', retryable: false } });
  });
});
