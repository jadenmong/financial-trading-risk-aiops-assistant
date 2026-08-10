package com.jadenmong.riskaiops.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.jadenmong.riskaiops.service.EvidenceService;
import com.jadenmong.riskaiops.repository.AuditEventMapper;

@Service
public class HashChainAuditService {
    public record AuditEvent(String eventId, Instant occurredAt, String subject, String clientId, List<String> scopes,
                             String action, String resource, String inputHash, String outputHash, String outcome,
                             String errorCode, String traceId, String previousHash, String eventHash) {}
    private final List<AuditEvent> events = new ArrayList<>();
    private final EvidenceService hashing;
    private final AuditEventMapper persistentMapper;
    private boolean failWrites;

    @Autowired
    public HashChainAuditService(EvidenceService hashing, ObjectProvider<AuditEventMapper> mapperProvider) {
        this.hashing = hashing;
        this.persistentMapper = mapperProvider.getIfAvailable();
    }
    public HashChainAuditService(EvidenceService hashing) { this.hashing = hashing; this.persistentMapper = null; }

    @Transactional
    public synchronized AuditEvent append(String subject, String clientId, List<String> scopes, String action,
                                          String resource, String inputHash, String outputHash, String outcome,
                                          String errorCode, String traceId) {
        if (failWrites) throw new IllegalStateException("audit storage unavailable");
        if (persistentMapper != null) persistentMapper.lockChain();
        String persistentPrevious = persistentMapper == null ? null : persistentMapper.lastHash();
        String previous = persistentPrevious != null ? persistentPrevious : events.isEmpty() ? "0".repeat(64) : events.getLast().eventHash();
        String id = UUID.randomUUID().toString(); Instant now = Instant.now();
        String hash = hashing.sha256(String.join("|", previous, id, now.toString(), subject, clientId, action, resource,
                inputHash, outputHash, outcome, errorCode == null ? "" : errorCode, traceId));
        AuditEvent event = new AuditEvent(id, now, subject, clientId, List.copyOf(scopes), action, resource, inputHash,
                outputHash, outcome, errorCode, traceId, previous, hash);
        if (persistentMapper != null && persistentMapper.insert(AuditEventMapper.PersistedAuditEvent.from(event)) != 1) {
            throw new IllegalStateException("audit insert did not affect exactly one row");
        }
        events.add(event); return event;
    }

    public synchronized List<AuditEvent> list() { return List.copyOf(events); }
    public synchronized boolean verify() {
        String previous = "0".repeat(64);
        for (AuditEvent event : events) {
            if (!event.previousHash().equals(previous)) return false;
            String expected = hashing.sha256(String.join("|", previous, event.eventId(), event.occurredAt().toString(), event.subject(), event.clientId(), event.action(), event.resource(), event.inputHash(), event.outputHash(), event.outcome(), event.errorCode() == null ? "" : event.errorCode(), event.traceId()));
            if (!expected.equals(event.eventHash())) return false;
            previous = event.eventHash();
        }
        return true;
    }
    public synchronized void simulateFailure(boolean value) { failWrites = value; }
}
