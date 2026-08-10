import { describe, expect, it } from 'vitest';

import { DecimalSchema, GenerateDailyReportInputSchema, GetMarketSnapshotInputSchema } from '../src/schemas.js';

describe('versioned schemas', () => {
  it('accepts bounded identifiers and decimal strings', () => {
    expect(GetMarketSnapshotInputSchema.parse({ instrumentId: 'CFFEX:IF2608', asOf: '2026-08-07T15:00:00+08:00' })).toBeDefined();
    expect(DecimalSchema.parse('123456789.1234567890')).toBe('123456789.1234567890');
    expect(GenerateDailyReportInputSchema.parse({ accountId: 'ACC_ALPHA_01', tradeDate: '2026-08-07' })).toBeDefined();
  });

  it('rejects float values, paths, URLs, free text and unknown keys', () => {
    expect(() => DecimalSchema.parse(1.2)).toThrow();
    expect(() => GetMarketSnapshotInputSchema.parse({ instrumentId: '../../etc/passwd' })).toThrow();
    expect(() => GetMarketSnapshotInputSchema.parse({ instrumentId: 'https://example.com', query: 'select *' })).toThrow();
  });
});
