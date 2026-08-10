package com.jadenmong.riskaiops.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jadenmong.riskaiops.audit.HashChainAuditService;
import com.jadenmong.riskaiops.repository.WorkflowStateMapper;

@Service
public class ReportWorkflowService {
    public enum Status { DRAFT, APPROVED, REJECTED }
    public enum Decision { APPROVE, REJECT }
    public record Report(String id, String diagnosisRunId, String accountId, LocalDate tradeDate, Status status,
                         String creator, String decidedBy, String decisionReason, int version, Instant createdAt,
                         Instant decidedAt, String sha256, String objectUri) {}
    public interface ImmutableObjectStore { String putIfAbsent(String key, String json, String html); }

    private final Map<String, Report> reports = new ConcurrentHashMap<>();
    private final DiagnosisWorkflowService diagnoses;
    private final EvidenceService hashing;
    private final HashChainAuditService audit;
    private final ImmutableObjectStore objectStore;
    private final WorkflowStateMapper mapper;
    private final RlsSessionService rls;
    private final TransactionalOutboxService outbox;

    public ReportWorkflowService(DiagnosisWorkflowService diagnoses, EvidenceService hashing, HashChainAuditService audit, ImmutableObjectStore objectStore) {
        this.diagnoses = diagnoses; this.hashing = hashing; this.audit = audit; this.objectStore = objectStore;
        this.mapper = null; this.rls = null; this.outbox = null;
    }

    @Autowired
    public ReportWorkflowService(DiagnosisWorkflowService diagnoses, EvidenceService hashing, HashChainAuditService audit,
                                 ImmutableObjectStore objectStore, ObjectProvider<WorkflowStateMapper> mapperProvider,
                                 ObjectProvider<RlsSessionService> rlsProvider, ObjectProvider<TransactionalOutboxService> outboxProvider) {
        this.diagnoses = diagnoses; this.hashing = hashing; this.audit = audit; this.objectStore = objectStore;
        this.mapper = mapperProvider.getIfAvailable(); this.rls = rlsProvider.getIfAvailable(); this.outbox = outboxProvider.getIfAvailable();
    }

    @Transactional
    public synchronized Report create(String diagnosisRunId, String actor) {
        if (mapper != null) return createPersistent(diagnosisRunId, actor);
        var diagnosis = diagnoses.get(diagnosisRunId);
        if (diagnosis.state() != DiagnosisWorkflowService.State.COMPLETED) throw new Conflict("Diagnosis must be completed");
        Report report = new Report(UUID.randomUUID().toString(), diagnosisRunId, diagnosis.accountId(), diagnosis.tradeDate(), Status.DRAFT,
                actor, null, null, 1, Instant.now(), null, null, null);
        audit.append(actor, "ops-console", List.of("report:write"), "report.create", report.id(), hashing.sha256(diagnosisRunId), hashing.sha256(report.toString()), "success", null, UUID.randomUUID().toString().replace("-", ""));
        reports.put(report.id(), report); return report;
    }

    @Transactional
    public synchronized Report decide(String id, Decision decision, String reason, int expectedVersion, String actor) {
        if (mapper != null) return decidePersistent(id, decision, reason, expectedVersion, actor);
        Report current = get(id);
        if (current.status() != Status.DRAFT) throw new Conflict("Approved or rejected reports are immutable");
        if (current.version() != expectedVersion) throw new Conflict("Report version changed");
        if (decision == Decision.APPROVE && current.creator().equals(actor)) throw new Conflict("Report creator cannot approve their own report");
        Instant now = Instant.now(); String hash = null, uri = null;
        Status status = decision == Decision.APPROVE ? Status.APPROVED : Status.REJECTED;
        if (status == Status.APPROVED) {
            String json = "{\"id\":\"" + current.id() + "\",\"diagnosisRunId\":\"" + current.diagnosisRunId() + "\",\"version\":" + (current.version() + 1) + "}";
            String html = "<!doctype html><html lang=\"zh-CN\"><body><h1>风险运维报告</h1><p>" + current.id() + "</p></body></html>";
            hash = hashing.sha256(json + "\n" + html);
            uri = objectStore.putIfAbsent(current.id() + "/" + hash, json, html);
        }
        Report updated = new Report(current.id(), current.diagnosisRunId(), current.accountId(), current.tradeDate(), status,
                current.creator(), actor, reason, current.version() + 1, current.createdAt(), now, hash, uri);
        audit.append(actor, "ops-console", List.of("report:approve"), "report.decision", id, hashing.sha256(decision + "|" + reason), hashing.sha256(updated.toString()), "success", null, UUID.randomUUID().toString().replace("-", ""));
        reports.put(id, updated); return updated;
    }

    @Transactional(readOnly = true)
    public Report get(String id) {
        if (mapper != null) {
            if (rls != null) rls.authorizeCurrentAccounts();
            var row = mapper.findReport(id);
            if (row == null) throw new NotFound("Report not found");
            return report(row);
        }
        Report report = reports.get(id); if (report == null) throw new NotFound("Report not found"); return report;
    }

    @Transactional(readOnly = true)
    public List<Report> list(int limit) {
        if (mapper != null) {
            if (rls != null) rls.authorizeCurrentAccounts();
            return mapper.listReports(Math.max(1, Math.min(limit, 200))).stream().map(this::report).toList();
        }
        return reports.values().stream().limit(Math.max(1, Math.min(limit, 200))).toList();
    }

    private Report createPersistent(String diagnosisRunId, String actor) {
        var diagnosis = diagnoses.get(diagnosisRunId);
        if (diagnosis.state() != DiagnosisWorkflowService.State.COMPLETED) throw new Conflict("Diagnosis must be completed");
        if (rls != null) rls.authorize(diagnosis.accountId());
        Report report = new Report(UUID.randomUUID().toString(), diagnosisRunId, diagnosis.accountId(), diagnosis.tradeDate(), Status.DRAFT,
                actor, null, null, 1, Instant.now(), null, null, null);
        mapper.insertReport(persisted(report));
        audit.append(actor, "ops-console", List.of("report:write"), "report.create", report.id(), hashing.sha256(diagnosisRunId), hashing.sha256(report.toString()), "success", null, UUID.randomUUID().toString().replace("-", ""));
        if (outbox != null) outbox.stage("report", report.id(), "report.lifecycle.v1", Map.of("reportId", report.id(), "status", report.status().name()));
        return report;
    }

    private Report decidePersistent(String id, Decision decision, String reason, int expectedVersion, String actor) {
        Report current = get(id);
        if (current.status() != Status.DRAFT) throw new Conflict("Approved or rejected reports are immutable");
        if (current.version() != expectedVersion) throw new Conflict("Report version changed");
        if (decision == Decision.APPROVE && current.creator().equals(actor)) throw new Conflict("Report creator cannot approve their own report");
        Instant now = Instant.now(); String hash = null, uri = null;
        Status status = decision == Decision.APPROVE ? Status.APPROVED : Status.REJECTED;
        if (status == Status.APPROVED) {
            String json = "{\"id\":\"" + current.id() + "\",\"diagnosisRunId\":\"" + current.diagnosisRunId() + "\",\"version\":" + (current.version() + 1) + "}";
            String html = "<!doctype html><html lang=\"zh-CN\"><body><h1>Risk AIOps Report</h1><p>" + current.id() + "</p></body></html>";
            hash = hashing.sha256(json + "\n" + html);
            uri = objectStore.putIfAbsent(current.id() + "/" + hash, json, html);
        }
        Report updated = new Report(current.id(), current.diagnosisRunId(), current.accountId(), current.tradeDate(), status,
                current.creator(), actor, reason, current.version() + 1, current.createdAt(), now, hash, uri);
        int affected = mapper.decideReport(new WorkflowStateMapper.PersistedReportDecision(updated.id(), updated.status().name(),
                updated.decidedBy(), updated.decisionReason(), updated.version(), updated.decidedAt(), updated.sha256(),
                updated.objectUri(), expectedVersion));
        if (affected != 1) throw new Conflict("Report version changed");
        audit.append(actor, "ops-console", List.of("report:approve"), "report.decision", id, hashing.sha256(decision + "|" + reason), hashing.sha256(updated.toString()), "success", null, UUID.randomUUID().toString().replace("-", ""));
        if (outbox != null) outbox.stage("report", updated.id(), "report.lifecycle.v1", Map.of("reportId", updated.id(), "status", updated.status().name()));
        return updated;
    }

    private WorkflowStateMapper.PersistedReport persisted(Report report) {
        return new WorkflowStateMapper.PersistedReport(report.id(), report.diagnosisRunId(), report.accountId(), report.tradeDate(),
                report.status().name(), report.creator(), report.decidedBy(), report.decisionReason(), report.version(),
                report.createdAt(), report.decidedAt(), report.sha256(), report.objectUri());
    }

    private Report report(WorkflowStateMapper.PersistedReport row) {
        return new Report(row.id(), row.diagnosisRunId(), row.accountId(), row.tradeDate(), Status.valueOf(row.status()),
                row.creator(), row.decidedBy(), row.decisionReason(), row.version(), row.createdAt(),
                row.decidedAt(), row.sha256(), row.objectUri());
    }
    public static class Conflict extends RuntimeException { public Conflict(String message) { super(message); } }
    public static class NotFound extends RuntimeException { public NotFound(String message) { super(message); } }
}
