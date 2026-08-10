package com.jadenmong.riskaiops.service;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jadenmong.riskaiops.domain.EventEnvelope;
import com.jadenmong.riskaiops.repository.OperationsMapper;

import tools.jackson.databind.ObjectMapper;

@Service
public class ReplayEventProcessor {
    public enum Disposition { APPLIED, DUPLICATE, OUT_OF_ORDER, FUTURE_REJECTED, STALE_APPLIED }
    private final Set<java.util.UUID> processed = new HashSet<>();
    private final Map<String, Instant> latestByPartition = new HashMap<>();
    private final OperationsMapper mapper;
    private final ObjectMapper objectMapper;

    public ReplayEventProcessor() {
        this.mapper = null;
        this.objectMapper = new ObjectMapper();
    }

    @Autowired
    public ReplayEventProcessor(ObjectProvider<OperationsMapper> mapperProvider, ObjectMapper objectMapper) {
        this.mapper = mapperProvider.getIfAvailable();
        this.objectMapper = objectMapper;
    }

    @Transactional
    public synchronized Disposition process(EventEnvelope event, Instant now) {
        if (mapper != null) return processPersistent(event, now);
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
        if (!Set.of("market.snapshot.v1", "position.snapshot.v1", "order.event.v1", "execution.event.v1",
                "order.lifecycle.v1", "execution.report.v1", "risk.limit.v1", "risk.breach.v1",
                "system.health.v1", "incident.signal.v1", "agent.run.v1", "report.lifecycle.v1", "ops.audit.v1")
                .contains(event.eventType())) throw new IllegalArgumentException("unsupported event type");
        if (event.source() == null || event.source().isBlank()) throw new IllegalArgumentException("source required");
        if (event.partitionKey() == null || event.partitionKey().isBlank()) throw new IllegalArgumentException("partitionKey required");
        if (event.payload() == null) throw new IllegalArgumentException("payload required");
    }

    private Disposition processPersistent(EventEnvelope event, Instant now) {
        validate(event);
        if (mapper.processedCount(event.eventId().toString()) > 0) return Disposition.DUPLICATE;
        String payloadJson = json(event.payload());
        String payloadHash = sha256(payloadJson);
        mapper.insertRawEvent(new OperationsMapper.RawEventInsert(event.eventId().toString(), event.schemaVersion(),
                event.eventType(), event.source(), event.sourceTimestamp(), event.publishedAt(), event.partitionKey(),
                sourceSequence(event.payload()), sourceHash(event.payload(), payloadHash), qualityFlags(event.payload()), payloadJson));
        Disposition disposition;
        if (event.sourceTimestamp().isAfter(now.plusSeconds(30))) {
            disposition = Disposition.FUTURE_REJECTED;
        } else {
            Instant latest = mapper.latestSourceTimestamp(event.partitionKey());
            if (latest != null && event.sourceTimestamp().isBefore(latest)) {
                disposition = Disposition.OUT_OF_ORDER;
            } else {
                disposition = Duration.between(event.sourceTimestamp(), now).compareTo(Duration.ofMinutes(5)) > 0
                        ? Disposition.STALE_APPLIED : Disposition.APPLIED;
            }
        }
        mapper.insertProcessedEvent(new OperationsMapper.ProcessedEventInsert(event.eventId().toString(), event.eventType(),
                event.schemaVersion(), event.sourceTimestamp(), disposition.name(), event.source(), event.partitionKey(), payloadHash));
        return disposition;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalArgumentException("Cannot serialize event payload", exception); }
    }

    private static Long sourceSequence(Map<String, Object> payload) {
        Object value = payload.get("sourceSequence");
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String sourceHash(Map<String, Object> payload, String fallback) {
        Object value = payload.get("sourceHash");
        return value instanceof String text && text.matches("^[0-9a-f]{64}$") ? text : fallback;
    }

    private static String qualityFlags(Map<String, Object> payload) {
        Object value = payload.get("qualityFlags");
        if (value instanceof List<?> list) return String.join(",", list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList());
        return "";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
