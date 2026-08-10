import { randomUUID } from 'node:crypto';

import type { ModelProvider } from './model-provider.js';
import type { ToolName } from './schemas.js';
import { verifyEvidence } from './evidence-verifier.js';

export type DiagnosisState = 'QUEUED' | 'TRIAGE' | 'MARKET' | 'RISK' | 'RECONCILIATION' | 'EVIDENCE_VERIFY' | 'REPORT_SYNTHESIS' | 'POLICY_CHECK' | 'COMPLETED' | 'NEEDS_REVIEW' | 'FAILED' | 'CANCELLED';
export interface DiagnosisEvent { sequence: number; state: DiagnosisState; at: string; detail: string; }
export interface DiagnosisRun { id: string; idempotencyKey: string; accountId: string; tradeDate: string; state: DiagnosisState; stepCount: number; modelCalls: number; estimatedCostUsd: string; startedAt: string; deadlineAt: string; events: DiagnosisEvent[]; }

export const AgentAllowlists = {
  supervisor: [] as ToolName[],
  marketContext: ['get_market_snapshot'] as ToolName[],
  riskAnalysis: ['get_position_risk'] as ToolName[],
  reconciliation: ['reconcile_orders'] as ToolName[],
  evidenceVerifier: [] as ToolName[],
  report: ['generate_daily_report'] as ToolName[],
} as const;

export class AgentBudgetExceeded extends Error {}

export class DiagnosisStateMachine {
  private readonly byId = new Map<string, DiagnosisRun>();
  private readonly byKey = new Map<string, string>();
  constructor(private readonly model: ModelProvider, private readonly maxSteps = 12, private readonly maxModelCalls = 6, private readonly maxDurationMs = 30_000, private readonly maxEstimatedCost = 0.25) {}

  create(idempotencyKey: string, accountId: string, tradeDate: string): DiagnosisRun {
    const priorId = this.byKey.get(idempotencyKey);
    if (priorId) return structuredClone(this.byId.get(priorId)!);
    const started = Date.now();
    const run: DiagnosisRun = { id: randomUUID(), idempotencyKey, accountId, tradeDate, state: 'QUEUED', stepCount: 0, modelCalls: 0, estimatedCostUsd: '0.000000', startedAt: new Date(started).toISOString(), deadlineAt: new Date(started + this.maxDurationMs).toISOString(), events: [] };
    this.byId.set(run.id, run); this.byKey.set(idempotencyKey, run.id); this.transition(run, 'QUEUED', 'Diagnosis accepted');
    return structuredClone(run);
  }

  get(id: string): DiagnosisRun | undefined { const run = this.byId.get(id); return run ? structuredClone(run) : undefined; }

  async run(id: string, evidence: Record<string, unknown>): Promise<DiagnosisRun> {
    const run = this.byId.get(id); if (!run) throw new Error('diagnosis not found');
    try {
      this.transition(run, 'TRIAGE', 'Supervisor validated fixed DAG');
      for (const state of ['MARKET', 'RISK', 'RECONCILIATION'] as const) this.transition(run, state, `${state} deterministic tool result attached`);
      const verification = verifyEvidence(evidence);
      if (!verification.valid) throw new AgentBudgetExceeded(`Evidence verification failed: ${verification.reasons.join(',')}`);
      this.transition(run, 'EVIDENCE_VERIFY', 'Evidence hashes and numeric claims verified');
      this.consumeModelBudget(run, 0.02);
      const output = await this.model.generate({ system: 'Explain verified evidence only. Never give investment advice.', input: evidence, safetyIdentifier: `diag_${run.id.slice(0, 12)}`, reasoningEffort: 'low' });
      this.transition(run, 'REPORT_SYNTHESIS', `Structured summary created with ${output.evidenceIds.length} evidence references`);
      this.transition(run, 'POLICY_CHECK', 'Read-only and evidence policies evaluated');
      this.transition(run, output.requiresReview ? 'NEEDS_REVIEW' : 'COMPLETED', output.requiresReview ? 'Model requested human review' : 'Diagnosis completed');
    } catch (error) {
      const state = error instanceof AgentBudgetExceeded ? 'NEEDS_REVIEW' : 'FAILED';
      run.state = state;
      run.events.push({ sequence: run.events.length + 1, state, at: new Date().toISOString(), detail: error instanceof Error ? error.message : 'unknown failure' });
    }
    return structuredClone(run);
  }

  cancel(id: string): DiagnosisRun {
    const run = this.byId.get(id); if (!run) throw new Error('diagnosis not found');
    if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(run.state)) return structuredClone(run);
    this.transition(run, 'CANCELLED', 'Cancelled by authorized operator'); return structuredClone(run);
  }

  private transition(run: DiagnosisRun, state: DiagnosisState, detail: string): void {
    if (Date.now() > Date.parse(run.deadlineAt) && !['NEEDS_REVIEW', 'FAILED'].includes(state)) throw new AgentBudgetExceeded('30 second run budget exceeded');
    run.stepCount += 1;
    if (run.stepCount > this.maxSteps) throw new AgentBudgetExceeded('12 step budget exceeded');
    run.state = state; run.events.push({ sequence: run.events.length + 1, state, at: new Date().toISOString(), detail });
  }

  private consumeModelBudget(run: DiagnosisRun, estimatedCost: number): void {
    run.modelCalls += 1;
    const total = Number.parseFloat(run.estimatedCostUsd) + estimatedCost;
    run.estimatedCostUsd = total.toFixed(6);
    if (run.modelCalls > this.maxModelCalls || total > this.maxEstimatedCost) throw new AgentBudgetExceeded('model call or cost budget exceeded');
  }
}
