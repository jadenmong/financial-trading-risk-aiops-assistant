package com.jadenmong.riskaiops.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;
import com.jadenmong.riskaiops.domain.EvidenceRef;
import com.jadenmong.riskaiops.repository.OperationsMapper;

@Service
public class EvidenceService {
    private final ObjectWriter writer;
    private final OperationsMapper operations;
    private final Map<String, EvidenceDocument> memory = new ConcurrentHashMap<>();

    public EvidenceService(ObjectMapper source) {
        this(source, (OperationsMapper) null);
    }

    public EvidenceService(ObjectMapper source, ObjectProvider<OperationsMapper> operationsProvider) {
        this(source, operationsProvider == null ? null : operationsProvider.getIfAvailable());
    }

    private EvidenceService(ObjectMapper source, OperationsMapper operations) {
        this.writer = source.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.operations = operations;
    }

    public EvidenceRef reference(String type, String version, Instant observedAt, Object value) {
        String payload = canonical(value);
        String hash = sha256(payload);
        EvidenceDocument document = new EvidenceDocument("ev_" + hash.substring(0, 24), type, version, hash, observedAt, payload);
        store(document);
        return new EvidenceRef(document.evidenceId(), type, version, hash, observedAt);
    }

    public EvidenceDocument get(String evidenceId) {
        if (operations != null) {
            var row = operations.findEvidence(evidenceId);
            if (row == null) throw new NotFound("Evidence not found");
            return new EvidenceDocument(row.evidenceId(), row.evidenceType(), row.evidenceVersion(), row.sha256(), row.observedAt(), row.payloadJson());
        }
        EvidenceDocument document = memory.get(evidenceId);
        if (document == null) throw new NotFound("Evidence not found");
        return document;
    }

    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String canonical(Object value) {
        try { return writer.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalArgumentException("Cannot canonicalize evidence", exception); }
    }

    private void store(EvidenceDocument document) {
        if (operations != null) {
            operations.insertEvidence(new OperationsMapper.EvidenceInsert(document.evidenceId(), document.type(),
                    document.version(), document.sha256(), document.observedAt(), document.payloadJson()));
        }
        memory.put(document.evidenceId(), document);
    }

    public record EvidenceDocument(String evidenceId, String type, String version, String sha256,
                                   Instant observedAt, String payloadJson) {}

    public static class NotFound extends RuntimeException { public NotFound(String message) { super(message); } }
}
