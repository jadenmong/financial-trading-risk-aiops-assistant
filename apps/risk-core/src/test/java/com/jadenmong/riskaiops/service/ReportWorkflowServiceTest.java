package com.jadenmong.riskaiops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import com.jadenmong.riskaiops.audit.HashChainAuditService;

class ReportWorkflowServiceTest {
    @Test
    void enforcesMakerCheckerOptimisticLockAndImmutability() {
        var evidence = new EvidenceService(new ObjectMapper());
        var audit = new HashChainAuditService(evidence);
        var diagnoses = new DiagnosisWorkflowService();
        var service = new ReportWorkflowService(diagnoses, evidence, audit, (key, json, html) -> "minio://test/" + key);
        var diagnosis = diagnoses.create("idem-report-001", "ACC_ALPHA_01", LocalDate.parse("2026-08-07"), "maker");
        var draft = service.create(diagnosis.id(), "maker");
        assertThatThrownBy(() -> service.decide(draft.id(), ReportWorkflowService.Decision.APPROVE, "self", 1, "maker")).isInstanceOf(ReportWorkflowService.Conflict.class);
        var approved = service.decide(draft.id(), ReportWorkflowService.Decision.APPROVE, "verified", 1, "checker");
        assertThat(approved.status()).isEqualTo(ReportWorkflowService.Status.APPROVED);
        assertThat(approved.sha256()).matches("^[0-9a-f]{64}$");
        assertThatThrownBy(() -> service.decide(draft.id(), ReportWorkflowService.Decision.REJECT, "late", 2, "checker-2")).isInstanceOf(ReportWorkflowService.Conflict.class);
        assertThat(audit.verify()).isTrue();
    }

    @Test
    void blocksApprovalWhenImmutableObjectStoreFails() {
        var evidence = new EvidenceService(new ObjectMapper()); var audit = new HashChainAuditService(evidence); var diagnoses = new DiagnosisWorkflowService();
        var service = new ReportWorkflowService(diagnoses, evidence, audit, (key, json, html) -> { throw new IllegalStateException("MinIO down"); });
        var diagnosis = diagnoses.create("idem-report-002", "ACC_ALPHA_01", LocalDate.parse("2026-08-07"), "maker"); var draft = service.create(diagnosis.id(), "maker");
        assertThatThrownBy(() -> service.decide(draft.id(), ReportWorkflowService.Decision.APPROVE, "ok", 1, "checker")).isInstanceOf(IllegalStateException.class);
        assertThat(service.get(draft.id()).status()).isEqualTo(ReportWorkflowService.Status.DRAFT);
    }

    @Test
    void createsOnlyOneDraftForEachCompletedDiagnosis() {
        var evidence = new EvidenceService(new ObjectMapper());
        var audit = new HashChainAuditService(evidence);
        var diagnoses = new DiagnosisWorkflowService();
        var service = new ReportWorkflowService(diagnoses, evidence, audit, (key, json, html) -> "minio://test/" + key);
        var diagnosis = diagnoses.create("idem-report-003", "ACC_ALPHA_01", LocalDate.parse("2026-08-07"), "maker");

        var first = service.create(diagnosis.id(), "maker");
        var repeated = service.create(diagnosis.id(), "maker");

        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(service.list(100)).containsExactly(first);
    }

    @Test
    void readsImmutableContentAfterApproval() {
        var evidence = new EvidenceService(new ObjectMapper());
        var audit = new HashChainAuditService(evidence);
        var diagnoses = new DiagnosisWorkflowService();
        Map<String, ReportWorkflowService.ReportContent> documents = new HashMap<>();
        var store = new ReportWorkflowService.ImmutableObjectStore() {
            @Override public String putIfAbsent(String key, String json, String html) {
                String uri = "minio://test/" + key;
                documents.putIfAbsent(uri, new ReportWorkflowService.ReportContent(json, html));
                return uri;
            }
            @Override public ReportWorkflowService.ReportContent get(String uri) { return documents.get(uri); }
        };
        var service = new ReportWorkflowService(diagnoses, evidence, audit, store);
        var diagnosis = diagnoses.create("idem-report-content", "ACC_ALPHA_01", LocalDate.parse("2026-08-07"), "maker");
        var draft = service.create(diagnosis.id(), "maker");
        var approved = service.decide(draft.id(), ReportWorkflowService.Decision.APPROVE, "verified", 1, "checker");

        var content = service.content(approved.id());
        assertThat(content.json()).contains(approved.id());
        assertThat(content.json()).contains("accountId", "diagnosis", "riskSnapshot", "reconciliation", "requiresReview");
        assertThat(content.html()).contains(approved.id());
        assertThat(content.html()).contains("风险快照", "对账结果", "诊断事件", "checker");
    }
}
