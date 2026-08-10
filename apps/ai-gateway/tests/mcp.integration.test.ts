import { Client } from '@modelcontextprotocol/client';
import { InMemoryTransport, type AuthInfo } from '@modelcontextprotocol/server';
import { describe, expect, it } from 'vitest';

import { InMemoryAuditSink } from '../src/audit.js';
import { createFinancialRiskMcpServer } from '../src/server.js';

async function connect(authInfo?: AuthInfo) {
  const audit = new InMemoryAuditSink();
  const server = createFinancialRiskMcpServer({ ...(authInfo ? { authInfo } : {}), audit });
  const client = new Client({ name: 'integration-test', version: '1.0.0' });
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  await Promise.all([server.connect(serverTransport), client.connect(clientTransport)]);
  return { client, server, audit };
}

describe('MCP v2 in-memory integration', () => {
  it('discovers only scoped tools and preserves read-only annotations', async () => {
    const authInfo: AuthInfo = { token: 'token', clientId: 'client', scopes: ['market:read'], extra: { subject: 'analyst', accounts: ['ACC_ALPHA_01'] } };
    const { client, server } = await connect(authInfo);
    try {
      const { tools } = await client.listTools();
      expect(tools.map((tool: { name: string }) => tool.name)).toEqual(['get_market_snapshot']);
      expect(tools[0]?.annotations).toMatchObject({ readOnlyHint: true, openWorldHint: false, destructiveHint: false });
    } finally { await Promise.all([client.close(), server.close()]); }
  });

  it('calls all read-only tools end-to-end over official in-memory transport', async () => {
    const { client, server, audit } = await connect();
    try {
      const { tools } = await client.listTools();
      expect(tools.map((tool: { name: string }) => tool.name).sort()).toEqual(['explain_reconciliation_breaks', 'generate_daily_report', 'get_incident_context', 'get_market_snapshot', 'get_position_risk', 'get_system_health', 'reconcile_orders', 'search_audit_events']);
      const calls = [
        ['get_market_snapshot', { instrumentId: 'SSE:600519' }],
        ['get_position_risk', { accountId: 'ACC_ALPHA_01' }],
        ['reconcile_orders', { accountId: 'ACC_ALPHA_01', tradeDate: '2026-08-07' }],
        ['generate_daily_report', { accountId: 'ACC_ALPHA_01', tradeDate: '2026-08-07' }],
        ['get_incident_context', { accountId: 'ACC_ALPHA_01' }],
        ['get_system_health', {}],
        ['explain_reconciliation_breaks', { accountId: 'ACC_ALPHA_01', tradeDate: '2026-08-07' }],
        ['search_audit_events', { limit: 10 }],
      ] as const;
      for (const [name, args] of calls) {
        const result = await client.callTool({ name, arguments: args });
        expect(result.isError).toBe(false);
        expect(result.structuredContent).toMatchObject({ schemaVersion: '1.0', ok: true, meta: { tool: name, toolVersion: '1.0.0' } });
      }
      expect(audit.records).toHaveLength(8);
    } finally { await Promise.all([client.close(), server.close()]); }
  });
});
