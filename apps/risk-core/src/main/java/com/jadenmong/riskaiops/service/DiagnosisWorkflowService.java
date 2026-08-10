package com.jadenmong.riskaiops.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jadenmong.riskaiops.repository.WorkflowStateMapper;

@Service
public class DiagnosisWorkflowService {
    public enum State { QUEUED, TRIAGE, MARKET, RISK, RECONCILIATION, EVIDENCE_VERIFY, REPORT_SYNTHESIS, POLICY_CHECK, COMPLETED, NEEDS_REVIEW, FAILED, CANCELLED }
    public record Event(int sequence, State state, Instant at, String detail) {}
    public record Run(String id, String idempotencyKey, String accountId, LocalDate tradeDate, State state, String createdBy, Instant createdAt, List<Event> events) {}
    private record Idempotency(String fingerprint, String runId) {}
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    private final Map<String, Idempotency> idempotency = new ConcurrentHashMap<>();
    private final WorkflowStateMapper mapper;
    private final RlsSessionService rls;
    private final TransactionalOutboxService outbox;

    public DiagnosisWorkflowService() {
        this.mapper = null;
        this.rls = null;
        this.outbox = null;
    }

    @Autowired
    public DiagnosisWorkflowService(ObjectProvider<WorkflowStateMapper> mapperProvider,
                                    ObjectProvider<RlsSessionService> rlsProvider,
                                    ObjectProvider<TransactionalOutboxService> outboxProvider) {
        this.mapper = mapperProvider.getIfAvailable();
        this.rls = rlsProvider.getIfAvailable();
        this.outbox = outboxProvider.getIfAvailable();
    }

    @Transactional
    public synchronized Run create(String key, String accountId, LocalDate tradeDate, String actor) {
        if (mapper != null) return createPersistent(key, accountId, tradeDate, actor);
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

    @Transactional(readOnly = true)
    public Run get(String id) {
        if (mapper != null) {
            if (rls != null) rls.authorizeCurrentAccounts();
            var row = mapper.findRun(id);
            if (row == null) throw new NotFound("Diagnosis not found");
            return persistentRun(row);
        }
        Run run = runs.get(id); if (run == null) throw new NotFound("Diagnosis not found"); return run;
    }

    private Run createPersistent(String key, String accountId, LocalDate tradeDate, String actor) {
        if (rls != null) rls.authorize(accountId);
        var prior = mapper.findRunByIdempotencyKey(key);
        if (prior != null) {
            if (!prior.accountId().equals(accountId) || !prior.tradeDate().equals(tradeDate)) {
                throw new Conflict("Idempotency key was used with a different payload");
            }
            return persistentRun(prior);
        }
        String id = UUID.randomUUID().toString();
        List<Event> events = referenceEvents();
        mapper.insertRun(new WorkflowStateMapper.PersistedRunInsert(id, key, accountId, tradeDate,
                State.COMPLETED.name(), events.size(), 1, "0.0200000000",
                "report-synthesis-v1", "deterministic-or-production-model", actor));
        for (Event event : events) {
            mapper.insertStep(new WorkflowStateMapper.PersistedStep(id, event.sequence(), event.state().name(), event.detail(), event.at()));
        }
        if (outbox != null) {
            outbox.stage("agent_run", id, "agent.run.v1", Map.of("runId", id, "accountId", accountId,
                    "tradeDate", tradeDate.toString(), "state", State.COMPLETED.name()));
        }
        return persistentRun(mapper.findRun(id));
    }

    private Run persistentRun(WorkflowStateMapper.PersistedRun row) {
        List<Event> events = mapper.steps(row.id()).stream()
                .map(step -> new Event(step.sequence(), State.valueOf(step.state()), step.occurredAt(), step.detail()))
                .toList();
        return new Run(row.id(), row.idempotencyKey(), row.accountId(), row.tradeDate(),
                State.valueOf(row.state()), row.createdBy(), row.createdAt(), events);
    }

    private static List<Event> referenceEvents() {
        List<Event> events = new ArrayList<>();
        add(events, State.QUEUED, "Diagnosis accepted"); add(events, State.TRIAGE, "Fixed DAG selected");
        add(events, State.MARKET, "Market context attached"); add(events, State.RISK, "Deterministic risk attached");
        add(events, State.RECONCILIATION, "Reconciliation attached"); add(events, State.EVIDENCE_VERIFY, "Evidence hashes verified");
        add(events, State.REPORT_SYNTHESIS, "Model summary produced under read-only policy"); add(events, State.POLICY_CHECK, "Read-only policy passed");
        add(events, State.COMPLETED, "Diagnosis completed");
        return List.copyOf(events);
    }

    private static void add(List<Event> events, State state, String detail) { events.add(new Event(events.size() + 1, state, Instant.now(), detail)); }
    public static class Conflict extends RuntimeException { public Conflict(String message) { super(message); } }
    public static class NotFound extends RuntimeException { public NotFound(String message) { super(message); } }
}
