package com.jadenmong.riskaiops.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import com.jadenmong.riskaiops.audit.HashChainAuditService;
import com.jadenmong.riskaiops.repository.WorkflowStateMapper;

@Service
public class ReportWorkflowService {
    public enum Status { DRAFT, APPROVED, REJECTED }
    public enum Decision { APPROVE, REJECT }
    public record Report(String id, String diagnosisRunId, String accountId, LocalDate tradeDate, Status status,
                         String creator, String decidedBy, String decisionReason, int version, Instant createdAt,
                         Instant decidedAt, String sha256, String objectUri) {}
    public record ReportContent(String json, String html) {}
    public interface ImmutableObjectStore {
        String putIfAbsent(String key, String json, String html);
        default ReportContent get(String objectUri) { throw new NotFound("Report content is unavailable"); }
    }

    private final Map<String, Report> reports = new ConcurrentHashMap<>();
    private final DiagnosisWorkflowService diagnoses;
    private final EvidenceService hashing;
    private final HashChainAuditService audit;
    private final ImmutableObjectStore objectStore;
    private final WorkflowStateMapper mapper;
    private final RlsSessionService rls;
    private final TransactionalOutboxService outbox;
    private final ReadOnlyToolFacade tools;
    private final ObjectMapper jsonMapper;

    public ReportWorkflowService(DiagnosisWorkflowService diagnoses, EvidenceService hashing, HashChainAuditService audit, ImmutableObjectStore objectStore) {
        this.diagnoses = diagnoses; this.hashing = hashing; this.audit = audit; this.objectStore = objectStore;
        this.mapper = null; this.rls = null; this.outbox = null;
        this.tools = null; this.jsonMapper = new ObjectMapper();
    }

    @Autowired
    public ReportWorkflowService(DiagnosisWorkflowService diagnoses, EvidenceService hashing, HashChainAuditService audit,
                                 ImmutableObjectStore objectStore, ObjectProvider<WorkflowStateMapper> mapperProvider,
                                 ObjectProvider<RlsSessionService> rlsProvider, ObjectProvider<TransactionalOutboxService> outboxProvider,
                                 ObjectProvider<ReadOnlyToolFacade> toolsProvider, ObjectProvider<ObjectMapper> mapperObjectProvider) {
        this.diagnoses = diagnoses; this.hashing = hashing; this.audit = audit; this.objectStore = objectStore;
        this.mapper = mapperProvider.getIfAvailable(); this.rls = rlsProvider.getIfAvailable(); this.outbox = outboxProvider.getIfAvailable();
        this.tools = toolsProvider.getIfAvailable(); this.jsonMapper = mapperObjectProvider.getIfAvailable();
    }

    @Transactional
    public synchronized Report create(String diagnosisRunId, String actor) {
        if (mapper != null) return createPersistent(diagnosisRunId, actor);
        var diagnosis = diagnoses.get(diagnosisRunId);
        if (diagnosis.state() != DiagnosisWorkflowService.State.COMPLETED) throw new Conflict("Diagnosis must be completed");
        var existing = reports.values().stream()
                .filter(report -> report.diagnosisRunId().equals(diagnosisRunId))
                .findFirst();
        if (existing.isPresent()) return existing.get();
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
            ReportContent content = buildContent(current, actor, reason, now);
            String json = content.json();
            String html = content.html();
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

    @Transactional(readOnly = true)
    public ReportContent content(String id) {
        Report report = get(id);
        if (report.status() != Status.APPROVED || report.objectUri() == null) throw new Conflict("Report content is available only after approval");
        if (rls != null) rls.authorize(report.accountId());
        return objectStore.get(report.objectUri());
    }

    private Report createPersistent(String diagnosisRunId, String actor) {
        var diagnosis = diagnoses.get(diagnosisRunId);
        if (diagnosis.state() != DiagnosisWorkflowService.State.COMPLETED) throw new Conflict("Diagnosis must be completed");
        if (rls != null) rls.authorize(diagnosis.accountId());
        var existing = mapper.findReportByDiagnosisRunId(diagnosisRunId);
        if (existing != null) return report(existing);
        Report report = new Report(UUID.randomUUID().toString(), diagnosisRunId, diagnosis.accountId(), diagnosis.tradeDate(), Status.DRAFT,
                actor, null, null, 1, Instant.now(), null, null, null);
        if (mapper.insertReport(persisted(report)) != 1) {
            var concurrentReport = mapper.findReportByDiagnosisRunId(diagnosisRunId);
            if (concurrentReport != null) return report(concurrentReport);
            throw new IllegalStateException("Report draft was not created");
        }
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
            ReportContent content = buildContent(current, actor, reason, now);
            String json = content.json();
            String html = content.html();
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

    /**
     * Builds the immutable report snapshot at approval time. Numeric facts come
     * from the deterministic read-only tools; model-generated text is kept
     * explicitly separate so it cannot change financial values.
     */
    private ReportContent buildContent(Report current, String approver, String decisionReason, Instant decidedAt) {
        var diagnosis = diagnoses.get(current.diagnosisRunId());
        Map<String, Object> document = new LinkedHashMap<>();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("reportId", current.id());
        metadata.put("diagnosisRunId", current.diagnosisRunId());
        metadata.put("accountId", current.accountId());
        metadata.put("tradeDate", current.tradeDate());
        metadata.put("version", current.version() + 1);
        metadata.put("status", Status.APPROVED.name());
        metadata.put("createdBy", current.creator());
        metadata.put("approvedBy", approver);
        metadata.put("createdAt", current.createdAt());
        metadata.put("approvedAt", decidedAt);
        metadata.put("decisionReason", decisionReason);
        document.put("report", metadata);

        Map<String, Object> diagnosisSnapshot = new LinkedHashMap<>();
        diagnosisSnapshot.put("state", diagnosis.state());
        diagnosisSnapshot.put("createdBy", diagnosis.createdBy());
        diagnosisSnapshot.put("createdAt", diagnosis.createdAt());
        diagnosisSnapshot.put("events", diagnosis.events());
        document.put("diagnosis", diagnosisSnapshot);

        document.put("riskSnapshot", safeTool("position-risk", () -> tools == null ? unavailable("Risk tool is not configured")
                : tools.positionRisk(current.accountId(), null, approver, null)));
        document.put("reconciliation", safeTool("order-reconciliation", () -> tools == null ? unavailable("Reconciliation tool is not configured")
                : tools.reconcile(current.accountId(), current.tradeDate(), approver, null)));

        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("summary", "本报告的金融数值和事实由 Risk Core 确定性计算生成。AI 解释摘要未在审批链路内生成，需人工复核风险与对账结果。");
        ai.put("requiresReview", true);
        ai.put("source", "risk-core-deterministic");
        document.put("ai", ai);
        document.put("disclaimer", "本报告仅用于风险运维诊断和审计留痕，不构成投资建议、交易指令或估值意见。");

        String json = serialize(document);
        return new ReportContent(json, renderHtml(document, current));
    }

    private Object safeTool(String name, Supplier<Object> operation) {
        try { return operation.get(); }
        catch (RuntimeException exception) { return unavailable(name + " unavailable: " + exception.getMessage()); }
    }

    private static Map<String, Object> unavailable(String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UNAVAILABLE"); result.put("reason", reason);
        return result;
    }

    private String serialize(Object value) {
        if (jsonMapper == null) return "{\"report\":\"content generated\"}";
        try { return jsonMapper.writer().with(SerializationFeature.INDENT_OUTPUT).writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Cannot serialize report content", exception); }
    }

    private String renderHtml(Map<String, Object> document, Report current) {
        String risk = escapeHtml(serialize(document.get("riskSnapshot")));
        String reconciliation = escapeHtml(serialize(document.get("reconciliation")));
        String events = document.get("diagnosis") instanceof Map<?, ?> diagnosis
                ? escapeHtml(serialize(diagnosis.get("events"))) : "[]";
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><title>风险运维报告 "
                + escapeHtml(current.id()) + "</title><style>body{font-family:system-ui,sans-serif;max-width:1100px;margin:32px auto;padding:0 20px;color:#17202a}"
                + "table{border-collapse:collapse;width:100%;margin:12px 0 24px}td,th{border:1px solid #d8dee4;padding:8px;text-align:left}th{background:#f3f6f8}"
                + "pre{background:#f6f8fa;padding:16px;overflow:auto;border-radius:6px}small{color:#57606a}</style></head><body>"
                + "<h1>风险运维报告</h1><table><tr><th>报告 ID</th><td>" + escapeHtml(current.id()) + "</td></tr>"
                + "<tr><th>诊断运行</th><td>" + escapeHtml(current.diagnosisRunId()) + "</td></tr>"
                + "<tr><th>账户</th><td>" + escapeHtml(current.accountId()) + "</td></tr>"
                + "<tr><th>交易日</th><td>" + escapeHtml(String.valueOf(current.tradeDate())) + "</td></tr>"
                + "<tr><th>创建人</th><td>" + escapeHtml(current.creator()) + "</td></tr>"
                + "<tr><th>批准人</th><td>" + escapeHtml(String.valueOf(document.get("report") instanceof Map<?, ?> metadata ? metadata.get("approvedBy") : "")) + "</td></tr>"
                + "<tr><th>批准时间</th><td>" + escapeHtml(String.valueOf(document.get("report") instanceof Map<?, ?> metadata ? metadata.get("approvedAt") : "")) + "</td></tr>"
                + "<tr><th>审批理由</th><td>" + escapeHtml(String.valueOf(document.get("report") instanceof Map<?, ?> metadata ? metadata.get("decisionReason") : "")) + "</td></tr></table>"
                + "<h2>风险快照</h2><pre>" + risk + "</pre><h2>对账结果</h2><pre>" + reconciliation + "</pre>"
                + "<h2>诊断事件</h2><pre>" + events + "</pre><p><small>本报告仅用于风险运维诊断和审计留痕，不构成投资建议、交易指令或估值意见。</small></p></body></html>";
    }

    private static String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    public static class Conflict extends RuntimeException { public Conflict(String message) { super(message); } }
    public static class NotFound extends RuntimeException { public NotFound(String message) { super(message); } }
}
