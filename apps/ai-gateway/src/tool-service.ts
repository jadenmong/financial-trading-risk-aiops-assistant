import type { AuthInfo } from '@modelcontextprotocol/server';

import type { AuditSink } from './audit.js';
import { hashAuditPayload } from './audit.js';
import { elapsedMs, startRequest, type EvidenceRef } from './evidence.js';
import { RiskCoreError, type RiskCoreClient } from './risk-core-client.js';
import { ToolDefinitions, type ToolName } from './schemas.js';
import { subjectOf } from './auth.js';
import type { TokenExchange } from './token-exchange.js';
import { ProviderError, type ModelProvider } from './model-provider.js';

const ModelTools = new Set<ToolName>(['generate_daily_report', 'explain_reconciliation_breaks']);
const ReportSynthesisPrompt = 'Explain only the supplied, verified evidence. Never provide investment advice or instructions to place, cancel, or modify a trade.';

export interface ToolEnvelope {
  schemaVersion: '1.0';
  ok: boolean;
  data?: Record<string, unknown>;
  error?: { code: string; message: string; retryable: boolean };
  meta: {
    requestId: string;
    traceId: string;
    tool: ToolName;
    toolVersion: '1.0.0';
    provider: string;
    dataVersion: string;
    dataAsOf?: string;
    qualityStatus: 'GOOD' | 'DEGRADED' | 'STALE' | 'REJECTED';
    subject: string;
    modelVersion: string | null;
    promptVersion: string | null;
    generatedAt: string;
    durationMs: number;
    evidenceRefs: EvidenceRef[];
  };
}

export class ToolService {
  constructor(
    private readonly core: RiskCoreClient,
    private readonly audit: AuditSink,
    private readonly tokenExchange?: TokenExchange,
    private readonly model?: ModelProvider,
  ) {}

  async execute(tool: ToolName, untrustedInput: unknown, authInfo?: AuthInfo): Promise<ToolEnvelope> {
    const request = startRequest(tool);
    const parsed = ToolDefinitions[tool].input.safeParse(untrustedInput);
    if (!parsed.success) return this.finishError(request, authInfo, untrustedInput, 'INVALID_INPUT', 'Input does not match the versioned tool schema', false);
    const input = parsed.data as Record<string, unknown>;
    const requiredScope = ToolDefinitions[tool].scope;
    if (authInfo && !authInfo.scopes.includes(requiredScope)) return this.finishError(request, authInfo, input, 'INSUFFICIENT_SCOPE', `Required scope: ${requiredScope}`, false);
    if (!this.accountAllowed(authInfo, input.accountId)) return this.finishError(request, authInfo, input, 'ACCESS_DENIED', 'Account is outside the authorized boundary', false);

    try {
      const coreToken = authInfo && this.tokenExchange ? await this.tokenExchange.exchange(authInfo.token, [requiredScope]) : undefined;
      const result = await this.core.execute(tool, input, coreToken);
      let data = result.data;
      let modelVersion: string | null = null;
      let promptVersion: string | null = null;
      if (this.model && ModelTools.has(tool)) {
        const modelOutput = await this.model.generate({
          system: ReportSynthesisPrompt,
          input: { data: result.data, evidenceRefs: result.evidenceRefs, qualityStatus: result.qualityStatus },
          safetyIdentifier: `mcp_${request.requestId.replaceAll('-', '').slice(0, 24)}`,
          reasoningEffort: 'low',
        });
        const allowedEvidenceIds = new Set(result.evidenceRefs.map(({ evidenceId }) => evidenceId));
        if (modelOutput.evidenceIds.some((evidenceId) => !allowedEvidenceIds.has(evidenceId))) {
          throw new ProviderError('schema', 'model referenced evidence outside the verified input');
        }
        data = { ...result.data, aiSummary: modelOutput.summary, aiEvidenceIds: modelOutput.evidenceIds, requiresReview: modelOutput.requiresReview };
        modelVersion = `${this.model.name}/${this.model.model}`;
        promptVersion = 'report-synthesis-v1';
      }
      const output: ToolEnvelope = {
        schemaVersion: '1.0',
        ok: true,
        data,
        meta: {
          requestId: request.requestId,
          traceId: request.traceId,
          tool,
          toolVersion: '1.0.0',
          provider: result.provider,
          dataVersion: result.dataVersion,
          dataAsOf: result.dataAsOf,
          qualityStatus: result.qualityStatus,
          subject: subjectOf(authInfo),
          modelVersion,
          promptVersion,
          generatedAt: new Date().toISOString(),
          durationMs: elapsedMs(request.startedAt),
          evidenceRefs: result.evidenceRefs,
        },
      };
      try {
        await this.audit.append(this.auditRecord(output, input, authInfo));
      } catch {
        return this.auditFailure(request, authInfo);
      }
      return output;
    } catch (error) {
      const mapped = this.mapError(error);
      return this.finishError(request, authInfo, input, mapped.code, mapped.message, mapped.retryable);
    }
  }

  private async finishError(
    request: ReturnType<typeof startRequest>,
    authInfo: AuthInfo | undefined,
    input: unknown,
    code: string,
    message: string,
    retryable: boolean,
  ): Promise<ToolEnvelope> {
    const output: ToolEnvelope = {
      schemaVersion: '1.0', ok: false,
      error: { code, message, retryable },
      meta: {
        requestId: request.requestId, traceId: request.traceId, tool: request.tool, toolVersion: '1.0.0',
        provider: 'risk-core', dataVersion: 'unknown', qualityStatus: 'REJECTED', subject: subjectOf(authInfo),
        modelVersion: null, promptVersion: null, generatedAt: new Date().toISOString(), durationMs: elapsedMs(request.startedAt), evidenceRefs: [],
      },
    };
    try {
      await this.audit.append(this.auditRecord(output, input, authInfo));
    } catch {
      return this.auditFailure(request, authInfo);
    }
    return output;
  }

  private auditFailure(request: ReturnType<typeof startRequest>, authInfo?: AuthInfo): ToolEnvelope {
    return {
      schemaVersion: '1.0', ok: false,
      error: { code: 'AUDIT_WRITE_FAILED', message: 'Audit persistence failed; protected data was blocked', retryable: true },
      meta: {
        requestId: request.requestId, traceId: request.traceId, tool: request.tool, toolVersion: '1.0.0', provider: 'risk-core', dataVersion: 'unknown',
        qualityStatus: 'REJECTED', subject: subjectOf(authInfo), modelVersion: null, promptVersion: null, generatedAt: new Date().toISOString(), durationMs: elapsedMs(request.startedAt), evidenceRefs: [],
      },
    };
  }

  private auditRecord(output: ToolEnvelope, input: unknown, authInfo?: AuthInfo) {
    return {
      timestamp: output.meta.generatedAt,
      requestId: output.meta.requestId,
      traceId: output.meta.traceId,
      subject: output.meta.subject,
      clientId: authInfo?.clientId ?? 'local-stdio',
      scopes: authInfo?.scopes ?? Object.values(ToolDefinitions).map((definition) => definition.scope),
      tool: output.meta.tool,
      operation: 'read' as const,
      inputHash: hashAuditPayload(input),
      outputHash: hashAuditPayload(output.ok ? output.data : output.error),
      outcome: output.ok ? 'success' as const : 'error' as const,
      ...(!output.ok && output.error ? { errorCode: output.error.code } : {}),
      durationMs: output.meta.durationMs,
      evidenceIds: output.meta.evidenceRefs.map(({ evidenceId }) => evidenceId),
    };
  }

  private accountAllowed(authInfo: AuthInfo | undefined, accountId: unknown): boolean {
    if (!authInfo || typeof accountId !== 'string') return true;
    const allowed = authInfo.extra?.accounts;
    return Array.isArray(allowed) && allowed.some((item) => item === accountId);
  }

  private mapError(error: unknown): { code: string; message: string; retryable: boolean } {
    if (error instanceof RiskCoreError) return { code: error.code, message: error.message, retryable: error.retryable };
    if (error instanceof ProviderError) return { code: `MODEL_${error.kind.toUpperCase()}`, message: error.message, retryable: ['timeout', 'rate_limit', 'server', 'circuit_open'].includes(error.kind) };
    if (error instanceof Error && (error.name === 'TimeoutError' || error.name === 'AbortError')) return { code: 'RISK_CORE_TIMEOUT', message: 'Risk Core timed out', retryable: true };
    return { code: 'INTERNAL_ERROR', message: 'Request failed; use traceId for investigation', retryable: false };
  }
}
