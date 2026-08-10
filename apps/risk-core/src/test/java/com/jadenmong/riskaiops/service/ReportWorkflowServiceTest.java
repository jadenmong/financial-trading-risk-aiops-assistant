package com.jadenmong.riskaiops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

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
}
