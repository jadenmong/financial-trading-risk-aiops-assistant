package com.jadenmong.riskaiops.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jadenmong.riskaiops.repository.OperationsMapper;

@Service
public class IncidentService {
    public enum Severity { INFO, WARNING, CRITICAL }
    public enum Status { OPEN, ACKED, CLOSED }
    public record Incident(String incidentId, String accountId, Severity severity, Status status, String title,
                           String sourceEventId, String evidenceId, String createdBy, String acknowledgedBy,
                           String closedBy, Instant createdAt, Instant acknowledgedAt, Instant closedAt,
                           String closeReason) {}

    private final Map<String, Incident> memory = new ConcurrentHashMap<>();
    private final OperationsMapper mapper;
    private final RlsSessionService rls;

    public IncidentService() {
        this.mapper = null;
        this.rls = null;
    }

    @Autowired
    public IncidentService(ObjectProvider<OperationsMapper> mapperProvider, ObjectProvider<RlsSessionService> rlsProvider) {
        this.mapper = mapperProvider.getIfAvailable();
        this.rls = rlsProvider.getIfAvailable();
    }

    @Transactional
    public Incident create(String accountId, Severity severity, String title, String sourceEventId, String evidenceId, String actor) {
        if (accountId != null && rls != null) rls.authorize(accountId);
        Incident incident = new Incident(UUID.randomUUID().toString(), accountId, severity, Status.OPEN, title,
                sourceEventId, evidenceId, actor, null, null, Instant.now(), null, null, null);
        if (mapper != null) {
            mapper.insertIncident(new OperationsMapper.IncidentInsert(incident.incidentId(), accountId, severity.name(),
                    title, sourceEventId, evidenceId, actor));
        }
        memory.put(incident.incidentId(), incident);
        return incident;
    }

    @Transactional(readOnly = true)
    public List<Incident> list(String accountId, int limit) {
        if (accountId != null && rls != null) rls.authorize(accountId);
        else if (rls != null) rls.authorizeCurrentAccounts();
        int bounded = Math.max(1, Math.min(limit, 200));
        if (mapper != null) return mapper.listIncidents(accountId, bounded).stream().map(this::incident).toList();
        return memory.values().stream()
                .filter(item -> accountId == null || accountId.equals(item.accountId()))
                .limit(bounded)
                .toList();
    }

    @Transactional(readOnly = true)
    public Incident get(String incidentId) {
        if (rls != null) rls.authorizeCurrentAccounts();
        if (mapper != null) {
            var row = mapper.findIncident(incidentId);
            if (row == null) throw new NotFound("Incident not found");
            return incident(row);
        }
        Incident incident = memory.get(incidentId);
        if (incident == null) throw new NotFound("Incident not found");
        return incident;
    }

    @Transactional
    public Incident acknowledge(String incidentId, String actor) {
        if (rls != null) rls.authorizeCurrentAccounts();
        if (mapper != null) {
            int affected = mapper.acknowledgeIncident(incidentId, actor);
            if (affected != 1) throw new Conflict("Incident is not open or is outside the authorized boundary");
            return get(incidentId);
        }
        Incident current = get(incidentId);
        if (current.status() != Status.OPEN) throw new Conflict("Incident is not open");
        Incident updated = new Incident(current.incidentId(), current.accountId(), current.severity(), Status.ACKED,
                current.title(), current.sourceEventId(), current.evidenceId(), current.createdBy(), actor,
                current.closedBy(), current.createdAt(), Instant.now(), current.closedAt(), current.closeReason());
        memory.put(incidentId, updated);
        return updated;
    }

    @Transactional
    public Incident close(String incidentId, String reason, String actor) {
        if (rls != null) rls.authorizeCurrentAccounts();
        if (mapper != null) {
            int affected = mapper.closeIncident(incidentId, actor, reason);
            if (affected != 1) throw new Conflict("Incident is already closed or is outside the authorized boundary");
            return get(incidentId);
        }
        Incident current = get(incidentId);
        if (current.status() == Status.CLOSED) throw new Conflict("Incident is already closed");
        Incident updated = new Incident(current.incidentId(), current.accountId(), current.severity(), Status.CLOSED,
                current.title(), current.sourceEventId(), current.evidenceId(), current.createdBy(),
                current.acknowledgedBy(), actor, current.createdAt(), current.acknowledgedAt(), Instant.now(), reason);
        memory.put(incidentId, updated);
        return updated;
    }

    private Incident incident(OperationsMapper.IncidentRow row) {
        return new Incident(row.incidentId(), row.accountId(), Severity.valueOf(row.severity()),
                Status.valueOf(row.status()), row.title(), row.sourceEventId(), row.evidenceId(),
                row.createdBy(), row.acknowledgedBy(), row.closedBy(), row.createdAt(),
                row.acknowledgedAt(), row.closedAt(), row.closeReason());
    }

    public static class Conflict extends RuntimeException { public Conflict(String message) { super(message); } }
    public static class NotFound extends RuntimeException { public NotFound(String message) { super(message); } }
}
