package com.jadenmong.riskaiops.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jadenmong.riskaiops.audit.HashChainAuditService;
import com.jadenmong.riskaiops.domain.EvidenceRef;
import com.jadenmong.riskaiops.domain.ReconciliationResult;
import com.jadenmong.riskaiops.domain.RiskResult;
import com.jadenmong.riskaiops.domain.ToolEnvelope;
import com.jadenmong.riskaiops.repository.RiskDataRepository;
import com.jadenmong.riskaiops.repository.ReconciliationDataRepository;

@Service
public class ReadOnlyToolFacade {
    private final RiskDataRepository data;
    private final RiskCalculationService risk;
    private final ReconciliationService reconciliation;
    private final EvidenceService evidence;
    private final HashChainAuditService audit;
    private final RlsSessionService rls;
    private final ReconciliationDataRepository reconciliationData;
    private final IncidentService incidents;

    public ReadOnlyToolFacade(RiskDataRepository data, RiskCalculationService risk, ReconciliationService reconciliation,
                              EvidenceService evidence, HashChainAuditService audit, RlsSessionService rls,
                              ReconciliationDataRepository reconciliationData, IncidentService incidents) {
        this.data = data; this.risk = risk; this.reconciliation = reconciliation; this.evidence = evidence; this.audit = audit; this.rls = rls; this.reconciliationData = reconciliationData;
        this.incidents = incidents;
    }

    @Transactional
    public ToolEnvelope<?> market(String instrumentId, Instant asOf, String subject, String traceId) {
        Instant requested = asOf == null ? Instant.now() : asOf;
        return execute("get_market_snapshot", Map.of("instrumentId", instrumentId, "asOf", requested.toString()), subject, traceId, () -> {
            var snapshot = data.marketSnapshot(instrumentId, requested);
            if (snapshot == null) throw new DomainException("SNAPSHOT_NOT_FOUND", "No snapshot at or before requested time", false);
            long freshness = Math.max(0, Duration.between(snapshot.observedAt(), requested).toSeconds());
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("instrumentId", snapshot.instrumentId()); output.put("symbol", snapshot.symbol()); output.put("venue", snapshot.venue());
            output.put("assetClass", snapshot.assetClass()); output.put("currency", snapshot.currency()); output.put("open", snapshot.open());
            output.put("high", snapshot.high()); output.put("low", snapshot.low()); output.put("close", snapshot.close()); output.put("prevClose", snapshot.prevClose());
            output.put("bid", snapshot.bid()); output.put("ask", snapshot.ask()); output.put("volume", snapshot.volume()); output.put("observedAt", snapshot.observedAt());
            output.put("freshnessSeconds", freshness); output.put("qualityFlags", freshness > 60 ? List.of("STALE") : snapshot.qualityFlags());
            return new Result(output, freshness > 60 ? "STALE" : "GOOD", snapshot.observedAt(), snapshot.dataVersion(), "market-snapshot");
        });
    }

    @Transactional
    public ToolEnvelope<?> positionRisk(String accountId, Instant asOf, String subject, String traceId) {
        rls.authorize(accountId);
        Instant requested = asOf == null ? Instant.now() : asOf;
        return execute("get_position_risk", Map.of("accountId", accountId, "asOf", requested.toString()), subject, traceId, () -> {
            var positionSet = data.positions(accountId, requested);
            if (positionSet.positions().isEmpty()) throw new DomainException("ACCOUNT_NOT_FOUND", "Account is absent or outside the authorized boundary", false);
            RiskResult output = risk.calculate(accountId, positionSet.positions(), new BigDecimal("70000000"), new BigDecimal("20000000"),
                    positionSet.observedAt(), requested,
                    new RiskCalculationService.Limits(new BigDecimal("180000000"), new BigDecimal("80000000"), new BigDecimal("0.40"), new BigDecimal("2.50"), new BigDecimal("0.70"), 60));
            String quality = output.limitBreaches().isEmpty() ? "GOOD" : "DEGRADED";
            return new Result(output, quality, positionSet.observedAt(), positionSet.dataVersion(), "position-risk");
        });
    }

    @Transactional
    public ToolEnvelope<?> reconcile(String accountId, LocalDate tradeDate, String subject, String traceId) {
        rls.authorize(accountId);
        return execute("reconcile_orders", Map.of("accountId", accountId, "tradeDate", tradeDate.toString()), subject, traceId, () -> {
            var positionSet = data.positions(accountId, Instant.now());
            if (positionSet.positions().isEmpty()) throw new DomainException("ACCOUNT_NOT_FOUND", "Account is absent or outside the authorized boundary", false);
            ReconciliationResult output = reconciliation.reconcile(accountId, tradeDate.toString(), reconciliationData.orders(accountId, tradeDate), reconciliationData.executions(accountId, tradeDate));
            return new Result(output, output.differences().isEmpty() ? "GOOD" : "DEGRADED", positionSet.observedAt(), positionSet.dataVersion(), "order-reconciliation");
        });
    }

    @Transactional
    public ToolEnvelope<?> reportPreview(String accountId, LocalDate tradeDate, String diagnosisRunId, String subject, String traceId) {
        rls.authorize(accountId);
        Map<String, Object> input = new LinkedHashMap<>(); input.put("accountId", accountId); input.put("tradeDate", tradeDate.toString()); if (diagnosisRunId != null) input.put("diagnosisRunId", diagnosisRunId);
        return execute("generate_daily_report", input, subject, traceId, () -> {
            var positionSet = data.positions(accountId, Instant.now());
            if (positionSet.positions().isEmpty()) throw new DomainException("ACCOUNT_NOT_FOUND", "Account is absent or outside the authorized boundary", false);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("previewId", UUID.randomUUID().toString()); output.put("accountId", accountId); output.put("tradeDate", tradeDate);
            output.put("diagnosisRunId", diagnosisRunId); output.put("status", "PREVIEW_ONLY");
            output.put("sections", List.of(
                    Map.of("title", "风险摘要", "status", "CRITICAL", "summary", "确定性风险计算发现集中度、杠杆或保证金利用率异常。"),
                    Map.of("title", "对账摘要", "status", "CRITICAL", "summary", "发现重复、孤立或字段差异，须人工复核。")));
            output.put("disclaimer", "虚构数据；仅用于运维诊断，不构成投资建议。正式草稿与审批必须使用 REST 工作流。");
            return new Result(output, "DEGRADED", positionSet.observedAt(), positionSet.dataVersion(), "daily-report-preview");
        });
    }

    @Transactional(readOnly = true)
    public ToolEnvelope<?> incidentContext(String incidentId, String accountId, String subject, String traceId) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (incidentId != null) input.put("incidentId", incidentId);
        if (accountId != null) input.put("accountId", accountId);
        return execute("get_incident_context", input, subject, traceId, () -> {
            Object data = incidentId == null ? incidents.list(accountId, 50) : incidents.get(incidentId);
            return new Result(Map.of("incidents", data), "GOOD", Instant.now(), "ops-incidents-v1", "incident-context");
        });
    }

    @Transactional(readOnly = true)
    public ToolEnvelope<?> systemHealth(String component, String subject, String traceId) {
        Map<String, Object> input = component == null ? Map.of() : Map.of("component", component);
        return execute("get_system_health", input, subject, traceId, () -> {
            var open = incidents.list(null, 100).stream().filter(item -> item.status() != IncidentService.Status.CLOSED).toList();
            boolean critical = open.stream().anyMatch(item -> item.severity() == IncidentService.Severity.CRITICAL);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("overallStatus", critical ? "DEGRADED" : "UP");
            output.put("component", component == null ? "platform" : component);
            output.put("openIncidents", open.size());
            output.put("readOnlyBoundary", true);
            return new Result(output, critical ? "DEGRADED" : "GOOD", Instant.now(), "system-health-v1", "system-health");
        });
    }

    @Transactional
    public ToolEnvelope<?> explainReconciliationBreaks(String accountId, LocalDate tradeDate, String subject, String traceId) {
        rls.authorize(accountId);
        return execute("explain_reconciliation_breaks", Map.of("accountId", accountId, "tradeDate", tradeDate.toString()), subject, traceId, () -> {
            ReconciliationResult result = reconciliation.reconcile(accountId, tradeDate.toString(), reconciliationData.orders(accountId, tradeDate), reconciliationData.executions(accountId, tradeDate));
            List<Map<String, Object>> explanations = result.differences().stream().map(difference -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", difference.type());
                row.put("severity", difference.severity());
                row.put("orderId", difference.orderId());
                row.put("executionId", difference.executionId());
                row.put("explanation", "Deterministic OMS/broker reconciliation break; operations review is required before any external correction.");
                return row;
            }).toList();
            return new Result(Map.of("accountId", accountId, "tradeDate", tradeDate, "summary", result.summary(), "breaks", explanations),
                    explanations.isEmpty() ? "GOOD" : "DEGRADED", Instant.now(), "order-reconciliation-v1", "reconciliation-explanation");
        });
    }

    @Transactional(readOnly = true)
    public ToolEnvelope<?> searchAuditEvents(String traceIdFilter, String subjectFilter, int limit, String subject, String traceId) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (traceIdFilter != null) input.put("traceId", traceIdFilter);
        if (subjectFilter != null) input.put("subject", subjectFilter);
        input.put("limit", limit);
        return execute("search_audit_events", input, subject, traceId, () -> {
            List<HashChainAuditService.AuditEvent> rows = audit.list().stream()
                    .filter(event -> traceIdFilter == null || traceIdFilter.equals(event.traceId()))
                    .filter(event -> subjectFilter == null || subjectFilter.equals(event.subject()))
                    .limit(Math.max(1, Math.min(limit, 200)))
                    .toList();
            return new Result(Map.of("events", rows), "GOOD", Instant.now(), "audit-events-v1", "audit-search");
        });
    }

    private ToolEnvelope<?> execute(String tool, Object input, String subject, String traceId, Supplier<Result> operation) {
        try {
            Result result = operation.get();
            EvidenceRef ref = evidence.reference(result.evidenceType(), result.dataVersion(), result.asOf(), result.data());
            ToolEnvelope.Meta meta = ToolEnvelope.meta(tool, result.quality(), result.asOf(), result.dataVersion(), List.of(ref), subject, normalizeTraceId(traceId));
            ToolEnvelope<Object> output = ToolEnvelope.success(result.data(), meta);
            try { appendAudit(subject, tool, input, output, "success", null, meta.traceId()); }
            catch (RuntimeException failure) { return auditFailure(tool, subject, meta.traceId()); }
            return output;
        } catch (DomainException domain) {
            ToolEnvelope.Meta meta = ToolEnvelope.meta(tool, "REJECTED", Instant.now(), "unknown", List.of(), subject, normalizeTraceId(traceId));
            ToolEnvelope<Object> output = ToolEnvelope.error(domain.code, domain.getMessage(), domain.retryable, meta);
            try { appendAudit(subject, tool, input, output, "error", domain.code, meta.traceId()); }
            catch (RuntimeException failure) { return auditFailure(tool, subject, meta.traceId()); }
            return output;
        } catch (RuntimeException unexpected) {
            ToolEnvelope.Meta meta = ToolEnvelope.meta(tool, "REJECTED", Instant.now(), "unknown", List.of(), subject, normalizeTraceId(traceId));
            ToolEnvelope<Object> output = ToolEnvelope.error("INTERNAL_ERROR", "Request failed; use traceId for investigation", false, meta);
            try { appendAudit(subject, tool, input, output, "error", "INTERNAL_ERROR", meta.traceId()); }
            catch (RuntimeException failure) { return auditFailure(tool, subject, meta.traceId()); }
            return output;
        }
    }

    private void appendAudit(String subject, String tool, Object input, Object output, String outcome, String errorCode, String traceId) {
        audit.append(subject, "ai-gateway", List.of("internal:" + tool), tool, "read-only-tool", evidence.sha256(String.valueOf(input)), evidence.sha256(String.valueOf(output)), outcome, errorCode, traceId);
    }

    private ToolEnvelope<?> auditFailure(String tool, String subject, String traceId) {
        return ToolEnvelope.error("AUDIT_WRITE_FAILED", "Audit persistence failed; protected data was blocked", true,
                ToolEnvelope.meta(tool, "REJECTED", Instant.now(), "unknown", List.of(), subject, traceId));
    }

    private static String normalizeTraceId(String traceparent) {
        if (traceparent != null && traceparent.matches("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$")) return traceparent.split("-")[1];
        return UUID.randomUUID().toString().replace("-", "");
    }
    private record Result(Object data, String quality, Instant asOf, String dataVersion, String evidenceType) {}
    private static final class DomainException extends RuntimeException {
        final String code; final boolean retryable;
        DomainException(String code, String message, boolean retryable) { super(message); this.code = code; this.retryable = retryable; }
    }
}
