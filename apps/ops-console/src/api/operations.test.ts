import { afterEach, describe, expect, it, vi } from 'vitest';
import { createDiagnosisIdempotencyKey } from './operations.js';

afterEach(() => vi.unstubAllGlobals());

describe('diagnosis idempotency keys', () => {
  it('creates a unique key for each new user-initiated run', () => {
    const first = createDiagnosisIdempotencyKey('ACC_ALPHA_01', '2026-08-07');
    const second = createDiagnosisIdempotencyKey('ACC_ALPHA_01', '2026-08-07');
    expect(first).toMatch(/^ops-console-ACC_ALPHA_01-2026-08-07-[A-Za-z0-9_-]{43}$/);
    expect(second).toMatch(/^ops-console-ACC_ALPHA_01-2026-08-07-[A-Za-z0-9_-]{43}$/);
    expect(first).not.toBe(second);
  });

  it('works on the HTTP deployment path where randomUUID is unavailable', () => {
    let sequence = 0;
    vi.stubGlobal('crypto', {
      getRandomValues(bytes: Uint8Array) {
        bytes.fill(++sequence);
        return bytes;
      },
    });

    expect(createDiagnosisIdempotencyKey('ACC_ALPHA_01', '2026-08-07'))
      .toMatch(/^ops-console-ACC_ALPHA_01-2026-08-07-[A-Za-z0-9_-]{43}$/);
  });
});
