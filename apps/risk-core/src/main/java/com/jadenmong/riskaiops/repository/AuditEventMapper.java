package com.jadenmong.riskaiops.repository;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
          input_sha256, output_sha256, duration_ms, outcome, error_code, trace_id, previous_hash, event_hash)
        VALUES(
          #{eventId}::uuid, #{occurredAt}, #{subject}, #{clientId}, string_to_array(#{scopesCsv}, ','), 'ALLOW',
          #{inputHash}, #{outputHash}, 0, #{outcome}, #{errorCode}, #{traceId}, #{previousHash}, #{eventHash})
        """)
    int insert(PersistedAuditEvent event);

    record PersistedAuditEvent(String eventId, java.time.Instant occurredAt, String subject, String clientId,
                               String scopesCsv, String inputHash, String outputHash, String outcome,
                               String errorCode, String traceId, String previousHash, String eventHash) {
        public static PersistedAuditEvent from(AuditEvent event) {
            return new PersistedAuditEvent(event.eventId(), event.occurredAt(), event.subject(), event.clientId(),
                    String.join(",", event.scopes()), event.inputHash(), event.outputHash(), event.outcome(),
                    event.errorCode(), event.traceId(), event.previousHash(), event.eventHash());
        }
    }
}
