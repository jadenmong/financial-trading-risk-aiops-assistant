package com.jadenmong.riskaiops.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;
import com.jadenmong.riskaiops.domain.EvidenceRef;

@Service
public class EvidenceService {
    private final ObjectWriter writer;

    public EvidenceService(ObjectMapper source) {
        this.writer = source.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public EvidenceRef reference(String type, String version, Instant observedAt, Object value) {
        String hash = sha256(canonical(value));
        return new EvidenceRef("ev_" + hash.substring(0, 24), type, version, hash, observedAt);
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
}
