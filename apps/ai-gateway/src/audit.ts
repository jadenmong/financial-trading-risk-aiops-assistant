import { appendFile, mkdir } from 'node:fs/promises';
import { dirname } from 'node:path';

import { canonicalJson, sha256 } from './evidence.js';

export interface AuditRecord {
  timestamp: string;
  requestId: string;
  traceId: string;
  subject: string;
  clientId: string;
  scopes: string[];
  tool: string;
  operation: 'read';
  inputHash: string;
  outputHash: string;
  outcome: 'success' | 'error';
  errorCode?: string;
  durationMs: number;
  evidenceIds: string[];
}

export interface AuditSink {
  append(record: AuditRecord): Promise<void>;
}

export class NdjsonAuditSink implements AuditSink {
  constructor(private readonly filePath: string) {}

  async append(record: AuditRecord): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true });
    await appendFile(this.filePath, `${canonicalJson(record)}\n`, { encoding: 'utf8', flag: 'a' });
  }
}

export class InMemoryAuditSink implements AuditSink {
  readonly records: AuditRecord[] = [];
  async append(record: AuditRecord): Promise<void> { this.records.push(structuredClone(record)); }
}

export class FailingAuditSink implements AuditSink {
  async append(): Promise<void> { throw new Error('simulated audit outage'); }
}

export function hashAuditPayload(value: unknown): string { return sha256(value); }
