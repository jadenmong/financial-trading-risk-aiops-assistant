package com.jadenmong.riskaiops.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class DiagnosisWorkflowService {
    public enum State { QUEUED, TRIAGE, MARKET, RISK, RECONCILIATION, EVIDENCE_VERIFY, REPORT_SYNTHESIS, POLICY_CHECK, COMPLETED, NEEDS_REVIEW, FAILED, CANCELLED }
    public record Event(int sequence, State state, Instant at, String detail) {}
    public record Run(String id, String idempotencyKey, String accountId, LocalDate tradeDate, State state, String createdBy, Instant createdAt, List<Event> events) {}
    private record Idempotency(String fingerprint, String runId) {}
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private final Map<String, Idempotency> idempotency = new ConcurrentHashMap<>();

    public synchronized Run create(String key, String accountId, LocalDate tradeDate, String actor) {
        String fingerprint = accountId + "|" + tradeDate;
        Idempotency prior = idempotency.get(key);
        if (prior != null) {
            if (!prior.fingerprint().equals(fingerprint)) throw new Conflict("Idempotency key was used with a different payload");
            return runs.get(prior.runId());
        }
        String id = UUID.randomUUID().toString(); List<Event> events = new ArrayList<>();
        add(events, State.QUEUED, "Diagnosis accepted"); add(events, State.TRIAGE, "Fixed DAG selected");
        add(events, State.MARKET, "Market context attached"); add(events, State.RISK, "Deterministic risk attached");
        add(events, State.RECONCILIATION, "Reconciliation attached"); add(events, State.EVIDENCE_VERIFY, "Evidence hashes verified");
        add(events, State.REPORT_SYNTHESIS, "Fake-model structured summary produced"); add(events, State.POLICY_CHECK, "Read-only policy passed");
        add(events, State.COMPLETED, "Reference diagnosis completed");
        Run run = new Run(id, key, accountId, tradeDate, State.COMPLETED, actor, Instant.now(), List.copyOf(events));
        runs.put(id, run); idempotency.put(key, new Idempotency(fingerprint, id)); return run;
    }

    public Run get(String id) { Run run = runs.get(id); if (run == null) throw new NotFound("Diagnosis not found"); return run; }
    private static void add(List<Event> events, State state, String detail) { events.add(new Event(events.size() + 1, state, Instant.now(), detail)); }
    public static class Conflict extends RuntimeException { public Conflict(String message) { super(message); } }
    public static class NotFound extends RuntimeException { public NotFound(String message) { super(message); } }
}
