import { z } from 'zod';

export const DecimalSchema = z.string().regex(/^-?(0|[1-9]\d*)(\.\d{1,10})?$/, 'must be a decimal string');
export const AccountIdSchema = z.string().regex(/^[A-Z0-9_-]{1,64}$/);
export const InstrumentIdSchema = z.string().regex(/^(SSE|SZSE|CFFEX):[A-Z0-9]{2,16}$/);
export const IsoDateTimeSchema = z.iso.datetime({ offset: true });
export const TradeDateSchema = z.iso.date();

export const GetMarketSnapshotInputSchema = z.object({
  instrumentId: InstrumentIdSchema,
  asOf: IsoDateTimeSchema.optional(),
}).strict();

export const GetPositionRiskInputSchema = z.object({
  accountId: AccountIdSchema,
  asOf: IsoDateTimeSchema.optional(),
}).strict();

export const ReconcileOrdersInputSchema = z.object({
  accountId: AccountIdSchema,
  tradeDate: TradeDateSchema,
}).strict();

export const GenerateDailyReportInputSchema = z.object({
  accountId: AccountIdSchema,
  tradeDate: TradeDateSchema,
  diagnosisRunId: z.uuid().optional(),
}).strict();

export const GetIncidentContextInputSchema = z.object({
  incidentId: z.uuid().optional(),
  accountId: AccountIdSchema.optional(),
}).strict();

export const GetSystemHealthInputSchema = z.object({
  component: z.string().regex(/^[A-Za-z0-9_.:-]{1,64}$/).optional(),
}).strict();

export const SearchAuditEventsInputSchema = z.object({
  traceId: z.string().regex(/^[0-9a-f]{32}$/).optional(),
  subject: z.string().regex(/^[A-Za-z0-9_.:@-]{1,128}$/).optional(),
  limit: z.number().int().min(1).max(200).default(100),
}).strict();

export const EvidenceRefSchema = z.object({
  evidenceId: z.string().min(1),
  type: z.string().min(1),
  version: z.string().min(1),
  sha256: z.string().regex(/^[0-9a-f]{64}$/),
  observedAt: IsoDateTimeSchema,
});

export const ToolEnvelopeSchema = z.object({
  schemaVersion: z.literal('1.0'),
  ok: z.boolean(),
  data: z.record(z.string(), z.unknown()).optional(),
  error: z.object({
    code: z.string(),
    message: z.string(),
    retryable: z.boolean(),
  }).optional(),
  meta: z.object({
    requestId: z.uuid(),
    traceId: z.string().regex(/^[0-9a-f]{32}$/),
    tool: z.string(),
    toolVersion: z.literal('1.0.0'),
    provider: z.string(),
    dataVersion: z.string(),
    dataAsOf: IsoDateTimeSchema.optional(),
    qualityStatus: z.enum(['GOOD', 'DEGRADED', 'STALE', 'REJECTED']),
    subject: z.string(),
    modelVersion: z.string().nullable(),
    promptVersion: z.string().nullable(),
    generatedAt: IsoDateTimeSchema,
    durationMs: z.number().int().nonnegative(),
    evidenceRefs: z.array(EvidenceRefSchema),
  }),
});

export type ToolName = keyof typeof ToolDefinitions;
export const ToolDefinitions = {
  get_market_snapshot: { input: GetMarketSnapshotInputSchema, scope: 'market:read' },
  get_position_risk: { input: GetPositionRiskInputSchema, scope: 'risk:read' },
  reconcile_orders: { input: ReconcileOrdersInputSchema, scope: 'reconciliation:read' },
  generate_daily_report: { input: GenerateDailyReportInputSchema, scope: 'report:preview' },
  get_incident_context: { input: GetIncidentContextInputSchema, scope: 'incident:read' },
  get_system_health: { input: GetSystemHealthInputSchema, scope: 'system:read' },
  explain_reconciliation_breaks: { input: ReconcileOrdersInputSchema, scope: 'reconciliation:read' },
  search_audit_events: { input: SearchAuditEventsInputSchema, scope: 'audit:read' },
} as const;
