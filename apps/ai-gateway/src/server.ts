import { McpServer, type AuthInfo } from '@modelcontextprotocol/server';

import type { AuditSink } from './audit.js';
import { NdjsonAuditSink } from './audit.js';
import type { RiskCoreClient } from './risk-core-client.js';
import { SampleRiskCoreClient } from './risk-core-client.js';
import {
  GenerateDailyReportInputSchema,
  GetMarketSnapshotInputSchema,
  GetPositionRiskInputSchema,
  ReconcileOrdersInputSchema,
  ToolDefinitions,
  ToolEnvelopeSchema,
  type ToolName,
} from './schemas.js';
import type { TokenExchange } from './token-exchange.js';
import { ToolService } from './tool-service.js';

export interface ServerOptions {
  authInfo?: AuthInfo;
  core?: RiskCoreClient;
  audit?: AuditSink;
  tokenExchange?: TokenExchange;
  auditPath?: string;
}

const metadata: Record<ToolName, { title: string; description: string }> = {
  get_market_snapshot: { title: '获取行情快照', description: '读取指定标的不晚于 asOf 的快照、新鲜度、质量标记和证据。' },
  get_position_risk: { title: '获取账户持仓风险', description: '读取授权账户的确定性敞口、PnL、保证金与六类限额结果。' },
  reconcile_orders: { title: '订单成交对账', description: '只读比对 OMS 与券商订单成交，返回版本化差异和证据。' },
  generate_daily_report: { title: '生成日报预览', description: '基于证据生成不落盘预览；正式草稿和审批仅通过 REST 工作流。' },
};

export function createFinancialRiskMcpServer(options: ServerOptions = {}): McpServer {
  const core = options.core ?? new SampleRiskCoreClient();
  const audit = options.audit ?? new NdjsonAuditSink(options.auditPath ?? 'runtime/gateway-audit.ndjson');
  const service = new ToolService(core, audit, options.tokenExchange);
  const server = new McpServer({ name: 'financial-trading-risk-aiops-assistant', version: '1.0.0' });
  const scopes = options.authInfo?.scopes;
  const visible = (tool: ToolName) => !scopes || scopes.includes(ToolDefinitions[tool].scope);
  const common = (tool: ToolName) => ({
    ...metadata[tool], outputSchema: ToolEnvelopeSchema,
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
  return server;
}
