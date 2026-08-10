import { Client } from '@modelcontextprotocol/client';
import { InMemoryTransport } from '@modelcontextprotocol/server';

import { InMemoryAuditSink } from '../src/audit.js';
import { createFinancialRiskMcpServer } from '../src/server.js';

const client = new Client({ name: 'risk-aiops-smoke', version: '1.0.0' });
const server = createFinancialRiskMcpServer({ audit: new InMemoryAuditSink() });
const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
await Promise.all([server.connect(serverTransport), client.connect(clientTransport)]);
const result = await client.callTool({ name: 'get_market_snapshot', arguments: { instrumentId: 'SSE:600519' } });
process.stdout.write(`${JSON.stringify(result.structuredContent, null, 2)}\n`);
await Promise.all([client.close(), server.close()]);
