import { McpServer, type AuthInfo } from '@modelcontextprotocol/server';

import type { AuditSink } from './audit.js';
import { NdjsonAuditSink } from './audit.js';
import type { RiskCoreClient } from './risk-core-client.js';
import { SampleRiskCoreClient } from './risk-core-client.js';
import {
  GenerateDailyReportInputSchema,
  GetIncidentContextInputSchema,
  GetMarketSnapshotInputSchema,
  GetPositionRiskInputSchema,
  GetSystemHealthInputSchema,
  ReconcileOrdersInputSchema,
  SearchAuditEventsInputSchema,
  ToolDefinitions,
  ToolEnvelopeSchema,
  type ToolName,
} from './schemas.js';
import type { TokenExchange } from './token-exchange.js';
import { ToolService } from './tool-service.js';
import { FakeModelProvider, type ModelProvider } from './model-provider.js';

export interface ServerOptions {
  authInfo?: AuthInfo;
  core?: RiskCoreClient;
  audit?: AuditSink;
  tokenExchange?: TokenExchange;
  auditPath?: string;
  model?: ModelProvider;
}

const metadata: Record<ToolName, { title: string; description: string }> = {
  get_market_snapshot: { title: 'Get market snapshot', description: 'Read a versioned market snapshot with freshness, quality flags and evidence.' },
  get_position_risk: { title: 'Get position risk', description: 'Read deterministic account exposure, PnL, margin and limit results.' },
  reconcile_orders: { title: 'Reconcile orders', description: 'Read OMS and broker execution differences with evidence references.' },
  generate_daily_report: { title: 'Generate daily report preview', description: 'Create a non-persistent report preview; governed drafts use REST workflows.' },
  get_incident_context: { title: 'Get incident context', description: 'Read incident state, evidence ids and operational context.' },
  get_system_health: { title: 'Get system health', description: 'Read AIOps platform health and open incident counts.' },
  explain_reconciliation_breaks: { title: 'Explain reconciliation breaks', description: 'Read deterministic reconciliation differences with operational explanations.' },
  search_audit_events: { title: 'Search audit events', description: 'Read append-only audit events by trace id or subject.' },
};

export function createFinancialRiskMcpServer(options: ServerOptions = {}): McpServer {
  const core = options.core ?? new SampleRiskCoreClient();
  const audit = options.audit ?? new NdjsonAuditSink(options.auditPath ?? 'runtime/gateway-audit.ndjson');
  const service = new ToolService(core, audit, options.tokenExchange, options.model ?? new FakeModelProvider());
  const server = new McpServer({ name: 'financial-trading-risk-aiops-assistant', version: '1.0.0' });
  const scopes = options.authInfo?.scopes;
  const visible = (tool: ToolName) => !scopes || scopes.includes(ToolDefinitions[tool].scope);
  const common = (tool: ToolName) => ({
    ...metadata[tool],
    outputSchema: ToolEnvelopeSchema,
    annotations: { readOnlyHint: true, openWorldHint: false, destructiveHint: false, idempotentHint: true },
  });
  const result = async (tool: ToolName, input: unknown) => {
    const output = await service.execute(tool, input, options.authInfo);
    return { isError: !output.ok, content: [{ type: 'text' as const, text: JSON.stringify(output) }], structuredContent: output };
  };

  if (visible('get_market_snapshot')) server.registerTool('get_market_snapshot', { ...common('get_market_snapshot'), inputSchema: GetMarketSnapshotInputSchema }, (input) => result('get_market_snapshot', input));
  if (visible('get_position_risk')) server.registerTool('get_position_risk', { ...common('get_position_risk'), inputSchema: GetPositionRiskInputSchema }, (input) => result('get_position_risk', input));
  if (visible('reconcile_orders')) server.registerTool('reconcile_orders', { ...common('reconcile_orders'), inputSchema: ReconcileOrdersInputSchema }, (input) => result('reconcile_orders', input));
  if (visible('generate_daily_report')) server.registerTool('generate_daily_report', { ...common('generate_daily_report'), inputSchema: GenerateDailyReportInputSchema }, (input) => result('generate_daily_report', input));
  if (visible('get_incident_context')) server.registerTool('get_incident_context', { ...common('get_incident_context'), inputSchema: GetIncidentContextInputSchema }, (input) => result('get_incident_context', input));
  if (visible('get_system_health')) server.registerTool('get_system_health', { ...common('get_system_health'), inputSchema: GetSystemHealthInputSchema }, (input) => result('get_system_health', input));
  if (visible('explain_reconciliation_breaks')) server.registerTool('explain_reconciliation_breaks', { ...common('explain_reconciliation_breaks'), inputSchema: ReconcileOrdersInputSchema }, (input) => result('explain_reconciliation_breaks', input));
  if (visible('search_audit_events')) server.registerTool('search_audit_events', { ...common('search_audit_events'), inputSchema: SearchAuditEventsInputSchema }, (input) => result('search_audit_events', input));
  return server;
}
