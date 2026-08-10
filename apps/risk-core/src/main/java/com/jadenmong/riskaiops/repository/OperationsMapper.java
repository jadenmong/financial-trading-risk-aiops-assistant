package com.jadenmong.riskaiops.repository;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OperationsMapper {
    @Insert("""
        INSERT INTO ops.transactional_outbox(outbox_id, aggregate_type, aggregate_id, topic,
                                             event_id, schema_version, payload)
        VALUES(#{outboxId}::uuid, #{aggregateType}, #{aggregateId}, #{topic},
               #{eventId}::uuid, #{schemaVersion}, #{payloadJson}::jsonb)
        """)
    int insertOutbox(OutboxInsert event);

    @Select("""
        SELECT outbox_id::text AS outboxId, event_id::text AS eventId, aggregate_type AS aggregateType,
               aggregate_id AS aggregateId, topic, schema_version AS schemaVersion,
               payload::text AS payloadJson, created_at AS createdAt, published_at AS publishedAt, attempts
          FROM ops.transactional_outbox
         WHERE published_at IS NULL
         ORDER BY created_at LIMIT #{limit}
        """)
    List<OutboxRow> pendingOutbox(@Param("limit") int limit);

    @Select("SELECT count(*) FROM ops.processed_event WHERE event_id = #{eventId}::uuid")
    int processedCount(@Param("eventId") String eventId);

    @Select("SELECT max(source_timestamp) FROM ops.processed_event WHERE partition_key = #{partitionKey}")
    Instant latestSourceTimestamp(@Param("partitionKey") String partitionKey);

    @Insert("""
        INSERT INTO ops.raw_event(event_id, schema_version, event_type, source_system, source_timestamp,
                                  published_at, partition_key, source_sequence, source_hash, quality_flags, payload)
        VALUES(#{eventId}::uuid, #{schemaVersion}, #{eventType}, #{sourceSystem}, #{sourceTimestamp},
               #{publishedAt}, #{partitionKey}, #{sourceSequence,jdbcType=BIGINT}, #{sourceHash},
               string_to_array(#{qualityFlagsCsv}, ','), #{payloadJson}::jsonb)
        ON CONFLICT (event_id) DO NOTHING
        """)
    int insertRawEvent(RawEventInsert event);

    @Insert("""
        INSERT INTO ops.processed_event(event_id, event_type, schema_version, source_timestamp,
                                        disposition, source_system, partition_key, payload_sha256)
        VALUES(#{eventId}::uuid, #{eventType}, #{schemaVersion}, #{sourceTimestamp},
               #{disposition}, #{sourceSystem}, #{partitionKey}, #{payloadSha256})
        ON CONFLICT (event_id) DO NOTHING
        """)
    int insertProcessedEvent(ProcessedEventInsert event);

    @Insert("""
        INSERT INTO ops.evidence_ref(evidence_id, evidence_type, evidence_version, sha256, observed_at, payload)
        VALUES(#{evidenceId}, #{evidenceType}, #{evidenceVersion}, #{sha256}, #{observedAt}, #{payloadJson}::jsonb)
        ON CONFLICT (evidence_id) DO NOTHING
        """)
    int insertEvidence(EvidenceInsert evidence);

    @Select("""
        SELECT evidence_id AS evidenceId, evidence_type AS evidenceType, evidence_version AS evidenceVersion,
               sha256, observed_at AS observedAt, payload::text AS payloadJson
          FROM ops.evidence_ref WHERE evidence_id = #{evidenceId}
        """)
    EvidenceRow findEvidence(@Param("evidenceId") String evidenceId);

    @Insert("""
        INSERT INTO ops.incident(incident_id, account_id, severity, status, title, source_event_id,
                                 evidence_id, created_by)
        VALUES(#{incidentId}::uuid, #{accountId,jdbcType=VARCHAR}, #{severity}, 'OPEN', #{title},
               #{sourceEventId,jdbcType=VARCHAR}::uuid, #{evidenceId,jdbcType=VARCHAR}, #{createdBy})
        """)
    int insertIncident(IncidentInsert incident);

    @Select("""
        SELECT incident_id::text AS incidentId, account_id AS accountId, severity, status, title,
               source_event_id::text AS sourceEventId, evidence_id AS evidenceId,
               created_by AS createdBy, acknowledged_by AS acknowledgedBy, closed_by AS closedBy,
               created_at AS createdAt, acknowledged_at AS acknowledgedAt, closed_at AS closedAt,
               close_reason AS closeReason
          FROM ops.incident
         WHERE (#{accountId,jdbcType=VARCHAR} IS NULL OR account_id = #{accountId,jdbcType=VARCHAR})
         ORDER BY created_at DESC LIMIT #{limit}
        """)
    List<IncidentRow> listIncidents(@Param("accountId") String accountId, @Param("limit") int limit);

    @Select("""
        SELECT incident_id::text AS incidentId, account_id AS accountId, severity, status, title,
               source_event_id::text AS sourceEventId, evidence_id AS evidenceId,
               created_by AS createdBy, acknowledged_by AS acknowledgedBy, closed_by AS closedBy,
               created_at AS createdAt, acknowledged_at AS acknowledgedAt, closed_at AS closedAt,
               close_reason AS closeReason
          FROM ops.incident WHERE incident_id = #{incidentId}::uuid
        """)
    IncidentRow findIncident(@Param("incidentId") String incidentId);

    @Update("""
        UPDATE ops.incident
           SET status = 'ACKED', acknowledged_by = #{actor}, acknowledged_at = now()
         WHERE incident_id = #{incidentId}::uuid AND status = 'OPEN'
        """)
    int acknowledgeIncident(@Param("incidentId") String incidentId, @Param("actor") String actor);

    @Update("""
        UPDATE ops.incident
           SET status = 'CLOSED', closed_by = #{actor}, closed_at = now(), close_reason = #{reason}
         WHERE incident_id = #{incidentId}::uuid AND status IN ('OPEN','ACKED')
        """)
    int closeIncident(@Param("incidentId") String incidentId, @Param("actor") String actor, @Param("reason") String reason);

    record OutboxInsert(String outboxId, String eventId, String aggregateType, String aggregateId,
                        String topic, String schemaVersion, String payloadJson) {}

    record OutboxRow(String outboxId, String eventId, String aggregateType, String aggregateId, String topic,
                     String schemaVersion, String payloadJson, Instant createdAt, Instant publishedAt, int attempts) {}

    record RawEventInsert(String eventId, String schemaVersion, String eventType, String sourceSystem,
                          Instant sourceTimestamp, Instant publishedAt, String partitionKey,
                          Long sourceSequence, String sourceHash, String qualityFlagsCsv, String payloadJson) {}

    record ProcessedEventInsert(String eventId, String eventType, String schemaVersion, Instant sourceTimestamp,
                                String disposition, String sourceSystem, String partitionKey, String payloadSha256) {}

    record EvidenceInsert(String evidenceId, String evidenceType, String evidenceVersion,
                          String sha256, Instant observedAt, String payloadJson) {}

    record EvidenceRow(String evidenceId, String evidenceType, String evidenceVersion,
                       String sha256, Instant observedAt, String payloadJson) {}

    record IncidentInsert(String incidentId, String accountId, String severity, String title,
                          String sourceEventId, String evidenceId, String createdBy) {}

    record IncidentRow(String incidentId, String accountId, String severity, String status, String title,
                       String sourceEventId, String evidenceId, String createdBy, String acknowledgedBy,
                       String closedBy, Instant createdAt, Instant acknowledgedAt, Instant closedAt,
                       String closeReason) {}
}
