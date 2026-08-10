package com.jadenmong.riskaiops.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.jadenmong.riskaiops.audit.HashChainAuditService;

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

    public ReportWorkflowService(DiagnosisWorkflowService diagnoses, EvidenceService hashing, HashChainAuditService audit, ImmutableObjectStore objectStore) {
        this.diagnoses = diagnoses; this.hashing = hashing; this.audit = audit; this.objectStore = objectStore;
    }

    public synchronized Report create(String diagnosisRunId, String actor) {
        var diagnosis = diagnoses.get(diagnosisRunId);
        if (diagnosis.state() != DiagnosisWorkflowService.State.COMPLETED) throw new Conflict("Diagnosis must be completed");
        Report report = new Report(UUID.randomUUID().toString(), diagnosisRunId, diagnosis.accountId(), diagnosis.tradeDate(), Status.DRAFT,
                actor, null, null, 1, Instant.now(), null, null, null);
        audit.append(actor, "ops-console", List.of("report:write"), "report.create", report.id(), hashing.sha256(diagnosisRunId), hashing.sha256(report.toString()), "success", null, UUID.randomUUID().toString().replace("-", ""));
        reports.put(report.id(), report); return report;
    }

    public synchronized Report decide(String id, Decision decision, String reason, int expectedVersion, String actor) {
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

    public Report get(String id) { Report report = reports.get(id); if (report == null) throw new NotFound("Report not found"); return report; }
    public static class Conflict extends RuntimeException { public Conflict(String message) { super(message); } }
    public static class NotFound extends RuntimeException { public NotFound(String message) { super(message); } }
}
