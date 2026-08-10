package com.jadenmong.riskaiops.domain;

import java.time.Instant;

public record EvidenceRef(String evidenceId, String type, String version, String sha256, Instant observedAt) {}
