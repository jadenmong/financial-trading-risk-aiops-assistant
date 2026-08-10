import { describe, expect, it } from 'vitest';

import { DiagnosisStateMachine } from '../src/agent-state-machine.js';
import { FakeModelProvider } from '../src/model-provider.js';

describe('persistent-shape fixed DAG', () => {
  it('is idempotent and completes through policy check', async () => {
    const machine = new DiagnosisStateMachine(new FakeModelProvider());
    const first = machine.create('idem-12345678', 'ACC_ALPHA_01', '2026-08-07');
    expect(machine.create('idem-12345678', 'ACC_ALPHA_01', '2026-08-07').id).toBe(first.id);
    const completed = await machine.run(first.id, { evidenceRefs: [{ evidenceId: 'ev_1', sha256: 'a'.repeat(64) }], numericClaims: [{ evidenceId: 'ev_1', actual: '1.0000000000', expected: '1.0000000000' }] });
    expect(completed.state).toBe('COMPLETED');
    expect(completed.events.map(({ state }) => state)).toEqual(['QUEUED', 'TRIAGE', 'MARKET', 'RISK', 'RECONCILIATION', 'EVIDENCE_VERIFY', 'REPORT_SYNTHESIS', 'POLICY_CHECK', 'COMPLETED']);
  });

  it('routes budget excess to human review', async () => {
    const machine = new DiagnosisStateMachine(new FakeModelProvider(), 3);
    const run = machine.create('idem-budget-1', 'ACC_ALPHA_01', '2026-08-07');
    const result = await machine.run(run.id, {});
    expect(result.state).toBe('NEEDS_REVIEW');
  });

  it('routes ungrounded numeric conclusions to human review', async () => {
    const machine = new DiagnosisStateMachine(new FakeModelProvider());
    const run = machine.create('idem-evidence-1', 'ACC_ALPHA_01', '2026-08-07');
    const result = await machine.run(run.id, { evidenceRefs: [{ evidenceId: 'ev_1', sha256: 'b'.repeat(64) }], numericClaims: [{ evidenceId: 'ev_1', actual: '2.0000000000', expected: '1.0000000000' }] });
    expect(result.state).toBe('NEEDS_REVIEW');
  });
});
