package com.jadenmong.riskaiops.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(UUID eventId, String schemaVersion, String eventType, String source,
                            Instant sourceTimestamp, Instant publishedAt, String partitionKey,
                            Map<String, Object> payload) {}
