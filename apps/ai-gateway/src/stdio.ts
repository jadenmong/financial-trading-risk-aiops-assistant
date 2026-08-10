#!/usr/bin/env node
import { resolve } from 'node:path';
import { serveStdio } from '@modelcontextprotocol/server/stdio';

import { createFinancialRiskMcpServer } from './server.js';

serveStdio(() => createFinancialRiskMcpServer({ auditPath: resolve('runtime/gateway-audit.ndjson') }), {
  onerror(error) { process.stderr.write(`[risk-aiops-mcp] ${error.message}\n`); },
});
