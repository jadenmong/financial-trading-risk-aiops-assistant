import type { AddressInfo } from 'node:net';
import { Client, StreamableHTTPClientTransport } from '@modelcontextprotocol/client';
import { afterEach, describe, expect, it } from 'vitest';

import { GatewayConfigSchema } from '../src/config.js';
import { createApp } from '../src/http.js';
import { SampleRiskCoreClient } from '../src/risk-core-client.js';

const servers: Array<ReturnType<ReturnType<typeof createApp>['listen']>> = [];
afterEach(async () => Promise.all(servers.splice(0).map((server) => new Promise<void>((resolve, reject) => server.close((error) => error ? reject(error) : resolve())))));

describe('protected Streamable HTTP MCP', () => {
  it('performs discovery and a structured tool call over a real HTTP socket', async () => {
    const config = GatewayConfigSchema.parse({ allowedHosts: ['127.0.0.1'], allowedOrigins: ['http://localhost:5173'], referenceAuth: true, auditPath: 'runtime/test-http-audit.ndjson' });
    const app = createApp({ config, core: new SampleRiskCoreClient() });
    const httpServer = app.listen(0, '127.0.0.1'); servers.push(httpServer);
    await new Promise<void>((resolve) => httpServer.once('listening', resolve));
    const port = (httpServer.address() as AddressInfo).port;
    const transport = new StreamableHTTPClientTransport(new URL(`http://127.0.0.1:${port}/mcp`), { requestInit: { headers: { authorization: 'Bearer reference-token' } } });
    const client = new Client({ name: 'http-integration-test', version: '1.0.0' });
    try {
      await client.connect(transport);
      const { tools } = await client.listTools();
      expect(tools).toHaveLength(4);
      const result = await client.callTool({ name: 'get_market_snapshot', arguments: { instrumentId: 'SSE:600519' } });
      expect(result.structuredContent).toMatchObject({ schemaVersion: '1.0', ok: true, meta: { tool: 'get_market_snapshot' } });
    } finally { await client.close(); }
  }, 20_000);
});
