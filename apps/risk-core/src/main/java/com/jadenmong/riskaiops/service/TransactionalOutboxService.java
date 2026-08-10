package com.jadenmong.riskaiops.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionalOutboxService {
    public record OutboxEvent(UUID outboxId, UUID eventId, String aggregateType, String aggregateId,
                              String topic, String schemaVersion, Map<String, Object> payload,
                              Instant createdAt, Instant publishedAt, int attempts) {}
    private final List<OutboxEvent> referenceEvents = new ArrayList<>();

    @Transactional
    public synchronized OutboxEvent stage(String aggregateType, String aggregateId, String topic, Map<String, Object> payload) {
        if (!topic.endsWith(".v1")) throw new IllegalArgumentException("topic must be versioned");
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), aggregateType, aggregateId, topic, "1.0", Map.copyOf(payload), Instant.now(), null, 0);
        referenceEvents.add(event); return event;
    }
    public synchronized List<OutboxEvent> pending() { return referenceEvents.stream().filter(event -> event.publishedAt() == null).toList(); }
}
