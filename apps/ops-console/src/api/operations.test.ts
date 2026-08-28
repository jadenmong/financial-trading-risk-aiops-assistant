import { describe, expect, it } from 'vitest';
import { createDiagnosisIdempotencyKey } from './operations.js';

describe('diagnosis idempotency keys', () => {
  it('creates a unique key for each new user-initiated run', () => {
    const first = createDiagnosisIdempotencyKey('ACC_ALPHA_01', '2026-08-07');
    const second = createDiagnosisIdempotencyKey('ACC_ALPHA_01', '2026-08-07');
    expect(first).toMatch(/^ops-console-ACC_ALPHA_01-2026-08-07-[0-9a-f-]{36}$/);
    expect(second).toMatch(/^ops-console-ACC_ALPHA_01-2026-08-07-[0-9a-f-]{36}$/);
    expect(first).not.toBe(second);
  });
});
