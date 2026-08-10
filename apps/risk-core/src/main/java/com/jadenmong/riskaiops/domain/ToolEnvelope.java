package com.jadenmong.riskaiops.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ToolEnvelope<T>(String schemaVersion, boolean ok, T data, ToolError error, Meta meta) {
    public record ToolError(String code, String message, boolean retryable) {}
    public record Meta(String requestId, String traceId, String tool, String toolVersion, String provider,
                       String dataVersion, Instant dataAsOf, String qualityStatus, String subject,
                       String modelVersion, String promptVersion, Instant generatedAt, long durationMs,
                       List<EvidenceRef> evidenceRefs) {}

    public static <T> ToolEnvelope<T> success(T data, Meta meta) {
        return new ToolEnvelope<>("1.0", true, data, null, meta);
    }

    public static <T> ToolEnvelope<T> error(String code, String message, boolean retryable, Meta meta) {
        return new ToolEnvelope<>("1.0", false, null, new ToolError(code, message, retryable), meta);
    }

    public static Meta meta(String tool, String quality, Instant asOf, List<EvidenceRef> evidence) {
        return meta(tool, quality, asOf, "2026-08-07.v1", evidence, "risk-core", UUID.randomUUID().toString().replace("-", ""));
    }

    public static Meta meta(String tool, String quality, Instant asOf, String dataVersion, List<EvidenceRef> evidence, String subject, String traceId) {
        return new Meta(UUID.randomUUID().toString(), traceId, tool, "1.0.0",
                "simulation-replay", dataVersion, asOf, quality, subject, null, null, Instant.now(), 0, evidence);
    }
}
