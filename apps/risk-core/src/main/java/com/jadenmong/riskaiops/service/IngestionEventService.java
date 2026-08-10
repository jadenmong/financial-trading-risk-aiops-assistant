package com.jadenmong.riskaiops.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jadenmong.riskaiops.domain.EventEnvelope;

@Service
public class IngestionEventService {
    public record IngestionResult(String eventId, ReplayEventProcessor.Disposition disposition, String incidentId) {}

    private final ReplayEventProcessor replay;
    private final IncidentService incidents;
    private final EvidenceService evidence;

    public IngestionEventService(ReplayEventProcessor replay, IncidentService incidents, EvidenceService evidence) {
        this.replay = replay;
        this.incidents = incidents;
        this.evidence = evidence;
    }

    @Transactional
    public IngestionResult ingest(EventEnvelope event, String actor) {
        var disposition = replay.process(event, Instant.now());
        String incidentId = null;
        if ("incident.signal.v1".equals(event.eventType())
                && (disposition == ReplayEventProcessor.Disposition.APPLIED || disposition == ReplayEventProcessor.Disposition.STALE_APPLIED)) {
            Map<String, Object> payload = event.payload();
            var ref = evidence.reference("incident-signal", event.schemaVersion(), event.sourceTimestamp(), payload);
            var incident = incidents.create(text(payload.get("accountId")), severity(payload.get("severity")),
                    text(payload.getOrDefault("title", "Operational signal")), event.eventId().toString(),
                    ref.evidenceId(), actor);
            incidentId = incident.incidentId();
        }
        return new IngestionResult(event.eventId().toString(), disposition, incidentId);
    }

    private static IncidentService.Severity severity(Object value) {
        if (value instanceof String text) {
            try { return IncidentService.Severity.valueOf(text); }
            catch (IllegalArgumentException ignored) { return IncidentService.Severity.WARNING; }
        }
        return IncidentService.Severity.WARNING;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
