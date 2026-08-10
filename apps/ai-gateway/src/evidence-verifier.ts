import { z } from 'zod';

const VerificationInputSchema = z.object({
  evidenceRefs: z.array(z.object({ evidenceId: z.string().min(1), sha256: z.string().regex(/^[0-9a-f]{64}$/) })),
  numericClaims: z.array(z.object({ evidenceId: z.string().min(1), actual: z.string(), expected: z.string() })).default([]),
}).passthrough();

export interface VerificationResult { valid: boolean; reasons: string[]; }

/** Exact string comparison is intentional: upstream deterministic engines normalize to DECIMAL(38,10). */
export function verifyEvidence(input: unknown): VerificationResult {
  const parsed = VerificationInputSchema.safeParse(input);
  if (!parsed.success) return { valid: false, reasons: ['EVIDENCE_SCHEMA_INVALID'] };
  const ids = new Set(parsed.data.evidenceRefs.map(({ evidenceId }) => evidenceId));
  const reasons: string[] = [];
  for (const claim of parsed.data.numericClaims) {
    if (!ids.has(claim.evidenceId)) reasons.push(`UNKNOWN_EVIDENCE:${claim.evidenceId}`);
    if (claim.actual !== claim.expected) reasons.push(`NUMERIC_MISMATCH:${claim.evidenceId}`);
  }
  return { valid: reasons.length === 0, reasons };
}
