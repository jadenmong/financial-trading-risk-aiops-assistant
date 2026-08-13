import { api } from './client.js';

export interface ToolEnvelope<T> {
  ok: boolean;
  data?: T;
  error?: { code: string; message: string };
  meta?: { qualityStatus: string; dataVersion: string; evidenceRefs: Array<{ evidenceId: string; sha256: string }> };
}

export interface RiskSnapshot {
  accountId: string;
  grossExposure: string;
  netExposure: string;
  leverage: string;
  marginUtilization: string;
  positions: Array<Record<string, unknown>>;
  limitBreaches: Array<Record<string, unknown>>;
}

export interface ReconciliationBreaks {
  summary: Record<string, number>;
  differences: Array<Record<string, unknown>>;
}

export interface Incident {
  incidentId: string;
  accountId?: string;
  severity: string;
  status: string;
  title: string;
  evidenceId?: string;
  createdAt: string;
}

export interface Report {
  id: string;
  accountId: string;
  creator: string;
  status: string;
  version: number;
}

export interface AuditEvent {
  occurredAt: string;
  subject: string;
  action: string;
  outcome: string;
  eventHash: string;
}

export function getRiskSnapshot(accountId = 'ACC_ALPHA_01') {
  return api<ToolEnvelope<RiskSnapshot>>(`/api/v1/risk-snapshots?accountId=${encodeURIComponent(accountId)}`);
}

export function getReconciliationBreaks(accountId = 'ACC_ALPHA_01', tradeDate = '2026-08-07') {
  return api<ToolEnvelope<ReconciliationBreaks>>(`/api/v1/reconciliation-breaks?accountId=${encodeURIComponent(accountId)}&tradeDate=${tradeDate}`);
}

export function listIncidents(accountId = 'ACC_ALPHA_01') {
  return api<Incident[]>(`/api/v1/incidents?accountId=${encodeURIComponent(accountId)}`);
}

export function listReports() {
  return api<Report[]>('/api/v1/reports?limit=100');
}

export function createReport(diagnosisRunId: string) {
  return api<Report>('/api/v1/reports', {
    method: 'POST',
    body: JSON.stringify({ diagnosisRunId }),
  });
}

export function listAuditEvents() {
  return api<AuditEvent[]>('/api/v1/audit-events');
}
