package com.jadenmong.riskaiops.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.jadenmong.riskaiops.domain.EventEnvelope;

@Service
public class ReplayEventProcessor {
    public enum Disposition { APPLIED, DUPLICATE, OUT_OF_ORDER, FUTURE_REJECTED, STALE_APPLIED }
    private final Set<java.util.UUID> processed = new HashSet<>();
    private final Map<String, Instant> latestByPartition = new HashMap<>();

    public synchronized Disposition process(EventEnvelope event, Instant now) {
        validate(event);
        if (processed.contains(event.eventId())) return Disposition.DUPLICATE;
        if (event.sourceTimestamp().isAfter(now.plusSeconds(30))) return Disposition.FUTURE_REJECTED;
        Instant latest = latestByPartition.get(event.partitionKey());
        if (latest != null && event.sourceTimestamp().isBefore(latest)) {
            processed.add(event.eventId());
            return Disposition.OUT_OF_ORDER;
        }
        processed.add(event.eventId()); latestByPartition.put(event.partitionKey(), event.sourceTimestamp());
        return Duration.between(event.sourceTimestamp(), now).compareTo(Duration.ofMinutes(5)) > 0 ? Disposition.STALE_APPLIED : Disposition.APPLIED;
    }

    private static void validate(EventEnvelope event) {
        if (!"1.0".equals(event.schemaVersion())) throw new IllegalArgumentException("unsupported event schema version");
        if (!Set.of("market.snapshot.v1", "position.snapshot.v1", "order.event.v1", "execution.event.v1", "risk.breach.v1", "agent.run.v1", "report.lifecycle.v1", "ops.audit.v1").contains(event.eventType())) throw new IllegalArgumentException("unsupported event type");
        if (event.partitionKey() == null || event.partitionKey().isBlank()) throw new IllegalArgumentException("partitionKey required");
    }
}
