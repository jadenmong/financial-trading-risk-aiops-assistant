import { makeEvidence, type EvidenceRef } from './evidence.js';
import type { ToolName } from './schemas.js';

export interface CoreResult {
  data: Record<string, unknown>;
  provider: string;
  dataVersion: string;
  dataAsOf: string;
  qualityStatus: 'GOOD' | 'DEGRADED' | 'STALE' | 'REJECTED';
  evidenceRefs: EvidenceRef[];
}

export class RiskCoreError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly retryable: boolean,
    readonly status: number,
  ) { super(message); }
}

export interface RiskCoreClient {
  execute(tool: ToolName, input: Record<string, unknown>, accessToken?: string): Promise<CoreResult>;
}

const paths: Record<ToolName, string> = {
  get_market_snapshot: '/internal/v1/market-snapshots/query',
  get_position_risk: '/internal/v1/position-risk/query',
  reconcile_orders: '/internal/v1/reconciliations/query',
  generate_daily_report: '/internal/v1/report-previews/query',
};

export class HttpRiskCoreClient implements RiskCoreClient {
  constructor(private readonly baseUrl: string, private readonly timeoutMs = 3_000) {}

  async execute(tool: ToolName, input: Record<string, unknown>, accessToken?: string): Promise<CoreResult> {
    const response = await fetch(new URL(paths[tool], this.baseUrl), {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        ...(accessToken ? { authorization: `Bearer ${accessToken}` } : {}),
      },
      body: JSON.stringify(input),
      signal: AbortSignal.timeout(this.timeoutMs),
    });
    const body = await response.json() as { ok?: boolean; data?: Record<string, unknown>; error?: { code?: string; message?: string; retryable?: boolean }; meta?: Omit<CoreResult, 'data'> };
    if (!response.ok || !body.ok || !body.data || !body.meta) {
      throw new RiskCoreError(body.error?.code ?? 'RISK_CORE_UNAVAILABLE', body.error?.message ?? 'Risk Core request failed', body.error?.retryable ?? response.status >= 500, response.status);
    }
    return { data: body.data, ...body.meta };
  }
}

/** Deterministic fictional source used by tests, stdio and the reference profile. */
export class SampleRiskCoreClient implements RiskCoreClient {
  private readonly asOf = '2026-08-07T07:00:00.000Z';
  private readonly version = '2026-08-07.v1';

  async execute(tool: ToolName, input: Record<string, unknown>): Promise<CoreResult> {
    if (tool === 'get_market_snapshot') return this.market(input);
    if (tool === 'get_position_risk') return this.risk(input);
    if (tool === 'reconcile_orders') return this.reconciliation(input);
    return this.report(input);
  }

  private result(type: string, data: Record<string, unknown>, qualityStatus: CoreResult['qualityStatus'] = 'GOOD'): CoreResult {
    return {
      data,
      provider: 'simulation-replay',
      dataVersion: this.version,
      dataAsOf: this.asOf,
      qualityStatus,
      evidenceRefs: [makeEvidence(type, this.version, this.asOf, data)],
    };
  }

  private market(input: Record<string, unknown>): CoreResult {
    const instrumentId = String(input.instrumentId);
    const fixtures: Record<string, Record<string, unknown>> = {
      'SSE:600519': { instrumentId, symbol: '600519', venue: 'SSE', assetClass: 'EQUITY', currency: 'CNY', open: '1412.0000000000', high: '1438.5000000000', low: '1408.0000000000', close: '1431.2500000000', prevClose: '1409.8000000000', bid: '1431.2000000000', ask: '1431.3000000000', volume: '2865400.0000000000', observedAt: this.asOf, freshnessSeconds: 0, qualityFlags: [] },
      'CFFEX:IF2608': { instrumentId, symbol: 'IF2608', venue: 'CFFEX', assetClass: 'INDEX_FUTURE', currency: 'CNY', open: '3980.2000000000', high: '4012.8000000000', low: '3968.6000000000', close: '4006.4000000000', prevClose: '3978.0000000000', bid: '4006.2000000000', ask: '4006.6000000000', volume: '82210.0000000000', settlementPrice: '4002.8000000000', contractMultiplier: '300.0000000000', observedAt: this.asOf, freshnessSeconds: 0, qualityFlags: [] },
    };
    const data = fixtures[instrumentId];
    if (!data) throw new RiskCoreError('SNAPSHOT_NOT_FOUND', `No snapshot for ${instrumentId}`, false, 404);
    if (input.asOf && String(input.asOf) < this.asOf) throw new RiskCoreError('SNAPSHOT_NOT_FOUND', `No snapshot at or before ${input.asOf}`, false, 404);
    return this.result('market-snapshot', data);
  }

  private risk(input: Record<string, unknown>): CoreResult {
    const accountId = String(input.accountId);
    if (!['ACC_ALPHA_01', 'ACC_ALPHA_02', 'ACC_BETA_01', 'ACC_BETA_02'].includes(accountId)) throw new RiskCoreError('ACCOUNT_NOT_FOUND', 'Account is absent or outside the authorized boundary', false, 404);
    return this.result('position-risk', {
      accountId,
      currency: 'CNY',
      asOf: this.asOf,
      grossExposure: '190305000.0000000000',
      netExposure: '142221000.0000000000',
      equityMarketValue: '70113000.0000000000',
      futuresNotional: '120192000.0000000000',
      deltaEquivalentExposure: '-48084000.0000000000',
      unrealizedPnl: '1685000.0000000000',
      marginUsed: '14423040.0000000000',
      leverage: '2.7186428571',
      marginUtilization: '0.7211520000',
      maxConcentration: '0.6142857143',
      positions: [
        { instrumentId: 'SSE:600519', side: 'LONG', quantity: '30000.0000000000', price: '1431.2500000000', marketValue: '42937500.0000000000', unrealizedPnl: '937500.0000000000', evidenceId: 'position-alpha-maotai' },
        { instrumentId: 'CFFEX:IF2608', side: 'SHORT', quantity: '100.0000000000', settlementPrice: '4002.8000000000', contractMultiplier: '300.0000000000', notional: '120084000.0000000000', margin: '14410080.0000000000', evidenceId: 'position-alpha-if' },
      ],
      limitBreaches: [
        { limitCode: 'SINGLE_INSTRUMENT_CONCENTRATION', actual: '0.6142857143', limit: '0.4000000000', severity: 'CRITICAL' },
        { limitCode: 'MARGIN_UTILIZATION', actual: '0.7211520000', limit: '0.7000000000', severity: 'WARNING' },
      ],
    }, 'DEGRADED');
  }

  private reconciliation(input: Record<string, unknown>): CoreResult {
    const accountId = String(input.accountId);
    return this.result('order-reconciliation', {
      accountId,
      tradeDate: String(input.tradeDate),
      summary: { matched: 8, differences: 4, critical: 2, warning: 2 },
      differences: [
        { type: 'DUPLICATE_EXECUTION', severity: 'CRITICAL', orderId: 'OMS-A-1003', executionId: 'BRK-E-7712', expected: '1', actual: '2', currency: 'CNY' },
        { type: 'ORPHAN_EXECUTION', severity: 'CRITICAL', orderId: null, executionId: 'BRK-E-7720', expected: null, actual: '500.0000000000', currency: 'CNY' },
        { type: 'QUANTITY_MISMATCH', severity: 'WARNING', orderId: 'OMS-A-1007', expected: '3000.0000000000', actual: '2800.0000000000', currency: 'CNY' },
        { type: 'STATUS_MISMATCH', severity: 'WARNING', orderId: 'OMS-A-1009', expected: 'FILLED', actual: 'PARTIALLY_FILLED', currency: 'CNY' },
      ],
    }, 'DEGRADED');
  }

  private report(input: Record<string, unknown>): CoreResult {
    const data = {
      previewId: `preview-${String(input.accountId).toLowerCase()}`,
      accountId: input.accountId,
      tradeDate: input.tradeDate,
      diagnosisRunId: input.diagnosisRunId ?? null,
      status: 'PREVIEW_ONLY',
      sections: [
        { title: '风险摘要', status: 'CRITICAL', summary: '单一标的集中度超限；结果来自确定性风险计算。' },
        { title: '对账摘要', status: 'CRITICAL', summary: '发现重复成交与孤立成交，需要人工复核。' },
      ],
      disclaimer: '虚构数据；仅用于运维诊断，不构成投资建议。正式草稿与审批必须使用 REST 工作流。',
    };
    return this.result('daily-report-preview', data, 'DEGRADED');
  }
}
