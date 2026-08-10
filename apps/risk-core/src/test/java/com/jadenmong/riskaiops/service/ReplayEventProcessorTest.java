package com.jadenmong.riskaiops.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.jadenmong.riskaiops.domain.EventEnvelope;

class ReplayEventProcessorTest {
    @Test
    void handlesDuplicateOutOfOrderFutureAndStaleEventsDeterministically() {
        var processor = new ReplayEventProcessor(); Instant now = Instant.parse("2026-08-07T07:00:00Z"); UUID id = UUID.randomUUID();
        assertThat(processor.process(event(id, now.minusSeconds(1)), now)).isEqualTo(ReplayEventProcessor.Disposition.APPLIED);
        assertThat(processor.process(event(id, now.minusSeconds(1)), now)).isEqualTo(ReplayEventProcessor.Disposition.DUPLICATE);
        assertThat(processor.process(event(UUID.randomUUID(), now.minusSeconds(2)), now)).isEqualTo(ReplayEventProcessor.Disposition.OUT_OF_ORDER);
        assertThat(processor.process(event(UUID.randomUUID(), now.plusSeconds(31)), now)).isEqualTo(ReplayEventProcessor.Disposition.FUTURE_REJECTED);
        assertThat(processor.process(new EventEnvelope(UUID.randomUUID(), "1.0", "market.snapshot.v1", "sim", now.minusSeconds(301), now, "other", Map.of()), now)).isEqualTo(ReplayEventProcessor.Disposition.STALE_APPLIED);
    }

    private static EventEnvelope event(UUID id, Instant timestamp) { return new EventEnvelope(id, "1.0", "market.snapshot.v1", "sim", timestamp, timestamp, "SSE:600519", Map.of()); }
}
