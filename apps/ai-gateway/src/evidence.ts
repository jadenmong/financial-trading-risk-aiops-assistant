import { createHash, randomBytes, randomUUID } from 'node:crypto';

import type { ToolName } from './schemas.js';

export interface EvidenceRef {
  evidenceId: string;
  type: string;
  version: string;
  sha256: string;
  observedAt: string;
}

export function canonicalJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (value !== null && typeof value === 'object') {
    return `{${Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, item]) => `${JSON.stringify(key)}:${canonicalJson(item)}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

export function sha256(value: unknown): string {
  return createHash('sha256').update(canonicalJson(value)).digest('hex');
}

export function makeEvidence(type: string, version: string, observedAt: string, value: unknown): EvidenceRef {
  const digest = sha256(value);
  return { evidenceId: `ev_${digest.slice(0, 24)}`, type, version, sha256: digest, observedAt };
}

export function startRequest(tool: ToolName) {
  return {
    requestId: randomUUID(),
    traceId: randomBytes(16).toString('hex'),
    tool,
    startedAt: process.hrtime.bigint(),
  };
}

export function elapsedMs(startedAt: bigint): number {
  return Math.max(0, Math.round(Number(process.hrtime.bigint() - startedAt) / 1_000_000));
}
