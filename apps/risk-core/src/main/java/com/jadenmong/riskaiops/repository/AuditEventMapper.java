package com.jadenmong.riskaiops.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

import com.jadenmong.riskaiops.audit.HashChainAuditService.AuditEvent;

@Mapper
public interface AuditEventMapper {
    @Select("SELECT pg_advisory_xact_lock(742091337)")
    void lockChain();

    @Select("SELECT event_hash FROM audit.audit_event ORDER BY sequence_id DESC LIMIT 1")
    String lastHash();

    @Insert("""
        INSERT INTO audit.audit_event(
          event_id, occurred_at, subject_id, client_id, scopes, authorization_decision,
          tool_name, input_sha256, output_sha256, duration_ms, outcome, error_code, trace_id, previous_hash, event_hash)
        VALUES(
          #{eventId}::uuid, #{occurredAt}, #{subject}, #{clientId}, string_to_array(#{scopesCsv}, ','), 'ALLOW',
          #{action}, #{inputHash}, #{outputHash}, 0, #{outcome}, #{errorCode,jdbcType=VARCHAR}, #{traceId}, #{previousHash}, #{eventHash})
        """)
    int insert(PersistedAuditEvent event);

    @Select("""
        SELECT event_id::text AS eventId, occurred_at AS occurredAt, subject_id AS subject,
               client_id AS clientId, array_to_string(scopes, ',') AS scopesCsv,
               COALESCE(tool_name, agent_step, 'audit') AS action,
               COALESCE(account_id, desk_id, '') AS resource,
               input_sha256 AS inputHash, output_sha256 AS outputHash, outcome, error_code AS errorCode,
               trace_id AS traceId, previous_hash AS previousHash, event_hash AS eventHash
          FROM audit.audit_event ORDER BY sequence_id DESC LIMIT #{limit}
        """)
    List<PersistedAuditEvent> listRecent(@Param("limit") int limit);

    record PersistedAuditEvent(String eventId, java.time.Instant occurredAt, String subject, String clientId,
                               String scopesCsv, String action, String resource, String inputHash, String outputHash,
                               String outcome, String errorCode, String traceId, String previousHash, String eventHash) {
        public static PersistedAuditEvent from(AuditEvent event) {
            return new PersistedAuditEvent(event.eventId(), event.occurredAt(), event.subject(), event.clientId(),
                    String.join(",", event.scopes()), event.action(), event.resource(), event.inputHash(), event.outputHash(), event.outcome(),
                    event.errorCode(), event.traceId(), event.previousHash(), event.eventHash());
        }
    }
}
