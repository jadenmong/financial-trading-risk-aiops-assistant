package com.jadenmong.riskaiops.api;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jadenmong.riskaiops.domain.ToolEnvelope;
import com.jadenmong.riskaiops.service.ReadOnlyToolFacade;

@RestController
@RequestMapping("/internal/v1")
public class InternalToolController {
    private final ReadOnlyToolFacade tools;
    public InternalToolController(ReadOnlyToolFacade tools) { this.tools = tools; }

    public record MarketRequest(@NotBlank @Pattern(regexp = "^(SSE|SZSE|CFFEX):[A-Z0-9]{2,16}$") String instrumentId, Instant asOf) {}
    public record RiskRequest(@NotBlank @Pattern(regexp = "^[A-Z0-9_-]{1,64}$") String accountId, Instant asOf) {}
    public record ReconcileRequest(@NotBlank @Pattern(regexp = "^[A-Z0-9_-]{1,64}$") String accountId, LocalDate tradeDate) {}
    public record ReportPreviewRequest(@NotBlank @Pattern(regexp = "^[A-Z0-9_-]{1,64}$") String accountId, LocalDate tradeDate, String diagnosisRunId) {}
    public record IncidentContextRequest(@Pattern(regexp = "^[0-9a-fA-F-]{36}$") String incidentId, @Pattern(regexp = "^[A-Z0-9_-]{1,64}$") String accountId) {}
    public record SystemHealthRequest(@Pattern(regexp = "^[A-Za-z0-9_.:-]{1,64}$") String component) {}
    public record AuditSearchRequest(@Pattern(regexp = "^[0-9a-f]{32}$") String traceId, @Pattern(regexp = "^[A-Za-z0-9_.:@-]{1,128}$") String subject, @Min(1) Integer limit) {}

    @PostMapping("/market-snapshots/query")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'market:read')")
    public ToolEnvelope<?> market(@Valid @RequestBody MarketRequest request, Principal principal, @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return tools.market(request.instrumentId(), request.asOf(), subject(principal), traceparent);
    }

    @PostMapping("/position-risk/query")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,#request.accountId(),'risk:read')")
    public ToolEnvelope<?> risk(@Valid @RequestBody RiskRequest request, Principal principal, @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return tools.positionRisk(request.accountId(), request.asOf(), subject(principal), traceparent);
    }

    @PostMapping("/reconciliations/query")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,#request.accountId(),'reconciliation:read')")
    public ToolEnvelope<?> reconcile(@Valid @RequestBody ReconcileRequest request, Principal principal, @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return tools.reconcile(request.accountId(), request.tradeDate(), subject(principal), traceparent);
    }

    @PostMapping("/report-previews/query")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,#request.accountId(),'report:preview')")
    public ToolEnvelope<?> preview(@Valid @RequestBody ReportPreviewRequest request, Principal principal, @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return tools.reportPreview(request.accountId(), request.tradeDate(), request.diagnosisRunId(), subject(principal), traceparent);
    }

    @PostMapping("/incidents/context/query")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,#request.accountId(),'incident:read')")
    public ToolEnvelope<?> incidentContext(@Valid @RequestBody IncidentContextRequest request, Principal principal, @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return tools.incidentContext(request.incidentId(), request.accountId(), subject(principal), traceparent);
    }

    @PostMapping("/system-health/query")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'system:read')")
    public ToolEnvelope<?> systemHealth(@Valid @RequestBody SystemHealthRequest request, Principal principal, @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return tools.systemHealth(request.component(), subject(principal), traceparent);
    }

    @PostMapping("/reconciliations/explain/query")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,#request.accountId(),'reconciliation:read')")
    public ToolEnvelope<?> explainReconciliation(@Valid @RequestBody ReconcileRequest request, Principal principal, @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return tools.explainReconciliationBreaks(request.accountId(), request.tradeDate(), subject(principal), traceparent);
    }

    @PostMapping("/audit-events/search/query")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'audit:read')")
    public ToolEnvelope<?> auditSearch(@Valid @RequestBody AuditSearchRequest request, Principal principal, @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return tools.searchAuditEvents(request.traceId(), request.subject(), request.limit() == null ? 100 : request.limit(), subject(principal), traceparent);
    }

    private static String subject(Principal principal) { return principal == null ? "reference-user" : principal.getName(); }
}
