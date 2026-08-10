package com.jadenmong.riskaiops.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jadenmong.riskaiops.repository.OperationsMapper;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class TransactionalOutboxService {
    public record OutboxEvent(UUID outboxId, UUID eventId, String aggregateType, String aggregateId,
                              String topic, String schemaVersion, Map<String, Object> payload,
                              Instant createdAt, Instant publishedAt, int attempts) {}
    private final List<OutboxEvent> referenceEvents = new ArrayList<>();
    private final OperationsMapper mapper;
    private final ObjectMapper objectMapper;

    public TransactionalOutboxService() {
        this.mapper = null;
        this.objectMapper = null;
    }

    @Autowired
    public TransactionalOutboxService(ObjectProvider<OperationsMapper> mapperProvider, ObjectMapper objectMapper) {
        this.mapper = mapperProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    @Transactional
    public synchronized OutboxEvent stage(String aggregateType, String aggregateId, String topic, Map<String, Object> payload) {
        if (!topic.endsWith(".v1")) throw new IllegalArgumentException("topic must be versioned");
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), aggregateType, aggregateId, topic, "1.0", Map.copyOf(payload), Instant.now(), null, 0);
        if (mapper != null) {
            mapper.insertOutbox(new OperationsMapper.OutboxInsert(event.outboxId().toString(), event.eventId().toString(),
                    aggregateType, aggregateId, topic, event.schemaVersion(), writeJson(payload)));
        }
        referenceEvents.add(event); return event;
    }

    public synchronized List<OutboxEvent> pending() {
        if (mapper != null) {
            return mapper.pendingOutbox(200).stream().map(row -> new OutboxEvent(UUID.fromString(row.outboxId()),
                    UUID.fromString(row.eventId()), row.aggregateType(), row.aggregateId(), row.topic(),
                    row.schemaVersion(), readJson(row.payloadJson()), row.createdAt(), row.publishedAt(), row.attempts())).toList();
        }
        return referenceEvents.stream().filter(event -> event.publishedAt() == null).toList();
    }

    private String writeJson(Map<String, Object> payload) {
        try { return objectMapper.writeValueAsString(payload); }
        catch (Exception exception) { throw new IllegalArgumentException("Cannot serialize outbox payload", exception); }
    }

    private Map<String, Object> readJson(String payload) {
        try { return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception exception) { throw new IllegalArgumentException("Cannot deserialize outbox payload", exception); }
    }
}
