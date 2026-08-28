package com.jadenmong.riskaiops.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.jadenmong.riskaiops.audit.HashChainAuditService;
import com.jadenmong.riskaiops.service.EvidenceService;
import com.jadenmong.riskaiops.service.DiagnosisWorkflowService;
import com.jadenmong.riskaiops.service.IncidentService;
import com.jadenmong.riskaiops.service.ReadOnlyToolFacade;
import com.jadenmong.riskaiops.service.ReportWorkflowService;

@RestController
@RequestMapping("/api/v1")
public class WorkflowController {
    public enum ReportContentFormat { html, json }
    public record CreateDiagnosis(@NotBlank @Pattern(regexp = "^[A-Z0-9_-]{1,64}$") String accountId, @NotNull LocalDate tradeDate) {}
    public record CreateReport(@NotBlank String diagnosisRunId) {}
    public record DecideReport(@NotNull ReportWorkflowService.Decision decision, @NotBlank String reason) {}
    public record CreateIncident(@Pattern(regexp = "^[A-Z0-9_-]{1,64}$") String accountId, @NotNull IncidentService.Severity severity, @NotBlank String title) {}
    public record CloseIncident(@NotBlank String reason) {}
    private final DiagnosisWorkflowService diagnoses; private final ReportWorkflowService reports; private final HashChainAuditService audit;
    private final ReadOnlyToolFacade tools; private final IncidentService incidents; private final EvidenceService evidence;
    public WorkflowController(DiagnosisWorkflowService diagnoses, ReportWorkflowService reports, HashChainAuditService audit,
                              ReadOnlyToolFacade tools, IncidentService incidents, EvidenceService evidence) {
        this.diagnoses = diagnoses; this.reports = reports; this.audit = audit; this.tools = tools; this.incidents = incidents; this.evidence = evidence;
    }

    @PostMapping("/diagnoses")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,#request.accountId(),'diagnosis:write')")
    public ResponseEntity<?> createDiagnosis(@RequestHeader("Idempotency-Key") @Pattern(regexp = "^.{8,128}$") String key, @Valid @RequestBody CreateDiagnosis request, Principal principal) {
        return ResponseEntity.accepted().body(diagnoses.create(key, request.accountId(), request.tradeDate(), actor(principal)));
    }

    @GetMapping("/diagnoses/{id}")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'diagnosis:read')")
    public DiagnosisWorkflowService.Run diagnosis(@PathVariable String id) { return diagnoses.get(id); }

    @GetMapping(value = "/diagnoses/{id}/events", produces = "text/event-stream")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'diagnosis:read')")
    public SseEmitter events(@PathVariable String id) throws IOException {
        SseEmitter emitter = new SseEmitter(10_000L);
        for (var event : diagnoses.get(id).events()) emitter.send(SseEmitter.event().id(String.valueOf(event.sequence())).name("diagnosis-state").data(event));
        emitter.complete(); return emitter;
    }

    @PostMapping("/reports")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'report:write')")
    public ResponseEntity<?> createReport(@Valid @RequestBody CreateReport request, Principal principal) { return ResponseEntity.status(HttpStatus.CREATED).body(reports.create(request.diagnosisRunId(), actor(principal))); }

    @GetMapping("/reports")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'report:read')")
    public List<ReportWorkflowService.Report> reports(@RequestParam(defaultValue = "100") int limit) { return reports.list(limit); }

    @GetMapping("/reports/{id}")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'report:read')")
    public ReportWorkflowService.Report report(@PathVariable String id) { return reports.get(id); }

    @GetMapping("/reports/{id}/content")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'report:read')")
    public ResponseEntity<byte[]> reportContent(@PathVariable String id,
                                                @RequestParam(defaultValue = "html") ReportContentFormat format) {
        var content = reports.content(id);
        String body = format == ReportContentFormat.html ? content.html() : content.json();
        MediaType contentType = format == ReportContentFormat.html ? MediaType.TEXT_HTML : MediaType.APPLICATION_JSON;
        String filename = "risk-report-" + id + "." + format.name();
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/reports/{id}/decisions")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'report:approve')")
    public ReportWorkflowService.Report decide(@PathVariable String id, @RequestHeader("If-Match") String ifMatch, @Valid @RequestBody DecideReport request, Principal principal) {
        return reports.decide(id, request.decision(), request.reason(), parseVersion(ifMatch), actor(principal));
    }

    @GetMapping("/audit-events")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'audit:read')")
    public List<HashChainAuditService.AuditEvent> auditEvents() { return audit.list(); }

    @GetMapping("/risk-snapshots")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,#accountId,'risk:read')")
    public Object riskSnapshot(@RequestParam @Pattern(regexp = "^[A-Z0-9_-]{1,64}$") String accountId,
                               @RequestParam(required = false) Instant asOf,
                               Principal principal,
                               @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return tools.positionRisk(accountId, asOf, actor(principal), traceparent);
    }

    @GetMapping("/reconciliation-breaks")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,#accountId,'reconciliation:read')")
    public Object reconciliationBreaks(@RequestParam @Pattern(regexp = "^[A-Z0-9_-]{1,64}$") String accountId,
                                       @RequestParam LocalDate tradeDate,
                                       Principal principal,
                                       @RequestHeader(value = "traceparent", required = false) String traceparent) {
        return tools.reconcile(accountId, tradeDate, actor(principal), traceparent);
    }

    @GetMapping("/evidence/{id}")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'evidence:read')")
    public EvidenceService.EvidenceDocument evidence(@PathVariable String id) { return evidence.get(id); }

    @GetMapping("/incidents")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,#accountId,'incident:read')")
    public List<IncidentService.Incident> incidents(@RequestParam(required = false) @Pattern(regexp = "^[A-Z0-9_-]{1,64}$") String accountId,
                                                    @RequestParam(defaultValue = "100") int limit) {
        return incidents.list(accountId, limit);
    }

    @PostMapping("/incidents")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,#request.accountId(),'incident:write')")
    public ResponseEntity<?> createIncident(@Valid @RequestBody CreateIncident request, Principal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidents.create(request.accountId(), request.severity(), request.title(), null, null, actor(principal)));
    }

    @PostMapping("/incidents/{id}/ack")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'incident:write')")
    public IncidentService.Incident acknowledgeIncident(@PathVariable String id, Principal principal) {
        return incidents.acknowledge(id, actor(principal));
    }

    @PostMapping("/incidents/{id}/close")
    @PreAuthorize("@accountAccessGuard.allowed(authentication,null,'incident:write')")
    public IncidentService.Incident closeIncident(@PathVariable String id, @Valid @RequestBody CloseIncident request, Principal principal) {
        return incidents.close(id, request.reason(), actor(principal));
    }

    @ExceptionHandler({DiagnosisWorkflowService.NotFound.class, ReportWorkflowService.NotFound.class, IncidentService.NotFound.class, EvidenceService.NotFound.class})
    ResponseEntity<?> notFound(RuntimeException exception) { return ResponseEntity.status(404).body(java.util.Map.of("code", "NOT_FOUND", "message", exception.getMessage())); }
    @ExceptionHandler({DiagnosisWorkflowService.Conflict.class, ReportWorkflowService.Conflict.class, IncidentService.Conflict.class})
    ResponseEntity<?> conflict(RuntimeException exception) { return ResponseEntity.status(409).body(java.util.Map.of("code", "CONFLICT", "message", exception.getMessage())); }

    private static int parseVersion(String value) {
        try { return Integer.parseInt(value.replace("\"", "")); }
        catch (NumberFormatException exception) { throw new ReportWorkflowService.Conflict("Invalid If-Match value"); }
    }
    private static String actor(Principal principal) { return principal == null ? "reference-user" : principal.getName(); }
}
