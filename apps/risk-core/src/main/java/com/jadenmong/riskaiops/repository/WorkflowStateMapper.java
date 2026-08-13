package com.jadenmong.riskaiops.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WorkflowStateMapper {
    @Select("""
        SELECT run_id::text AS id, idempotency_key AS idempotencyKey, account_id AS accountId,
               trade_date AS tradeDate, state, created_by AS createdBy, created_at AS createdAt
          FROM ai.agent_run WHERE run_id = #{id}::uuid
        """)
    PersistedRun findRun(@Param("id") String id);

    @Select("""
        SELECT run_id::text AS id, idempotency_key AS idempotencyKey, account_id AS accountId,
               trade_date AS tradeDate, state, created_by AS createdBy, created_at AS createdAt
          FROM ai.agent_run WHERE idempotency_key = #{idempotencyKey}
        """)
    PersistedRun findRunByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Insert("""
        INSERT INTO ai.agent_run(run_id, idempotency_key, account_id, trade_date, state, step_count,
                                 model_calls, estimated_cost_usd, prompt_version, model_version, created_by)
        VALUES(#{id}::uuid, #{idempotencyKey}, #{accountId}, #{tradeDate}, #{state}, #{stepCount},
               #{modelCalls}, #{estimatedCostUsd}, #{promptVersion}, #{modelVersion}, #{createdBy})
        """)
    int insertRun(PersistedRunInsert run);

    @Update("UPDATE ai.agent_run SET state = #{state}, step_count = #{stepCount}, updated_at = now() WHERE run_id = #{id}::uuid")
    int updateRunState(@Param("id") String id, @Param("state") String state, @Param("stepCount") int stepCount);

    @Insert("""
        INSERT INTO ai.agent_step(run_id, sequence, state, detail, occurred_at)
        VALUES(#{runId}::uuid, #{sequence}, #{state}, #{detail}, #{occurredAt})
        """)
    int insertStep(PersistedStep step);

    @Select("""
        SELECT sequence, state, occurred_at AS occurredAt, detail
          FROM ai.agent_step WHERE run_id = #{runId}::uuid ORDER BY sequence
        """)
    List<PersistedStepRow> steps(@Param("runId") String runId);

    @Insert("""
        INSERT INTO ai.report(report_id, run_id, account_id, trade_date, status, creator, version, created_at)
        VALUES(#{id}::uuid, #{diagnosisRunId}::uuid, #{accountId}, #{tradeDate}, #{status}, #{creator}, #{version}, #{createdAt})
        ON CONFLICT (run_id) DO NOTHING
        """)
    int insertReport(PersistedReport report);

    @Select("""
        SELECT report_id::text AS id, run_id::text AS diagnosisRunId, account_id AS accountId,
               trade_date AS tradeDate, status, creator, decided_by AS decidedBy,
               decision_reason AS decisionReason, version, created_at AS createdAt,
               decided_at AS decidedAt, content_sha256 AS sha256,
               COALESCE(html_object_uri, json_object_uri) AS objectUri
          FROM ai.report WHERE report_id = #{id}::uuid
        """)
    PersistedReport findReport(@Param("id") String id);

    @Select("""
        SELECT report_id::text AS id, run_id::text AS diagnosisRunId, account_id AS accountId,
               trade_date AS tradeDate, status, creator, decided_by AS decidedBy,
               decision_reason AS decisionReason, version, created_at AS createdAt,
               decided_at AS decidedAt, content_sha256 AS sha256,
               COALESCE(html_object_uri, json_object_uri) AS objectUri
          FROM ai.report WHERE run_id = #{diagnosisRunId}::uuid
        """)
    PersistedReport findReportByDiagnosisRunId(@Param("diagnosisRunId") String diagnosisRunId);

    @Select("""
        SELECT report_id::text AS id, run_id::text AS diagnosisRunId, account_id AS accountId,
               trade_date AS tradeDate, status, creator, decided_by AS decidedBy,
               decision_reason AS decisionReason, version, created_at AS createdAt,
               decided_at AS decidedAt, content_sha256 AS sha256,
               COALESCE(html_object_uri, json_object_uri) AS objectUri
          FROM ai.report ORDER BY created_at DESC LIMIT #{limit}
        """)
    List<PersistedReport> listReports(@Param("limit") int limit);

    @Update("""
        UPDATE ai.report
           SET status = #{status}, decided_by = #{decidedBy}, decision_reason = #{decisionReason,jdbcType=VARCHAR},
               version = #{version}, decided_at = #{decidedAt}, content_sha256 = #{sha256,jdbcType=CHAR},
               json_object_uri = #{objectUri,jdbcType=VARCHAR}, html_object_uri = #{objectUri,jdbcType=VARCHAR}
         WHERE report_id = #{id}::uuid AND version = #{expectedVersion} AND status = 'DRAFT'
        """)
    int decideReport(PersistedReportDecision decision);

    record PersistedRun(String id, String idempotencyKey, String accountId, LocalDate tradeDate,
                        String state, String createdBy, Instant createdAt) {}

    record PersistedRunInsert(String id, String idempotencyKey, String accountId, LocalDate tradeDate,
                              String state, int stepCount, int modelCalls, String estimatedCostUsd,
                              String promptVersion, String modelVersion, String createdBy) {}

    record PersistedStep(String runId, int sequence, String state, String detail, Instant occurredAt) {}

    record PersistedStepRow(int sequence, String state, Instant occurredAt, String detail) {}

    record PersistedReport(String id, String diagnosisRunId, String accountId, LocalDate tradeDate,
                           String status, String creator, String decidedBy, String decisionReason,
                           int version, Instant createdAt, Instant decidedAt, String sha256, String objectUri) {}

    record PersistedReportDecision(String id, String status, String decidedBy, String decisionReason,
                                   int version, Instant decidedAt, String sha256, String objectUri,
                                   int expectedVersion) {}
}
