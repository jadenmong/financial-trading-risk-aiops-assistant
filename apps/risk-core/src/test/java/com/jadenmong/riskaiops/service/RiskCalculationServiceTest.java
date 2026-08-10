package com.jadenmong.riskaiops.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.jadenmong.riskaiops.repository.SampleDataRepository;

class RiskCalculationServiceTest {
    private final SampleDataRepository data = new SampleDataRepository();
    private final RiskCalculationService service = new RiskCalculationService();

    @Test
    void calculatesEquityAndFuturesWithBigDecimalAndTenDecimalPlaces() {
        var result = service.calculate("ACC_ALPHA_01", data.positions("ACC_ALPHA_01", SampleDataRepository.DATA_AS_OF).positions(), new BigDecimal("70000000"), new BigDecimal("20000000"),
                SampleDataRepository.DATA_AS_OF, SampleDataRepository.DATA_AS_OF,
                new RiskCalculationService.Limits(new BigDecimal("180000000"), new BigDecimal("80000000"), new BigDecimal("0.40"), new BigDecimal("2.50"), new BigDecimal("0.70"), 60));
        assertThat(result.equityMarketValue()).isEqualTo("54713500.0000000000");
        assertThat(result.futuresNotional()).isEqualTo("120084000.0000000000");
        assertThat(result.grossExposure()).isEqualTo("174797500.0000000000");
        assertThat(result.netExposure()).isEqualTo("-65370500.0000000000");
        assertThat(result.unrealizedPnl()).isEqualTo("1929500.0000000000");
        assertThat(result.marginUsed()).isEqualTo("14410080.0000000000");
        assertThat(result.limitChecks()).extracting(item -> item.limitCode()).containsExactly(
                "GROSS_EXPOSURE", "NET_EXPOSURE", "SINGLE_INSTRUMENT_CONCENTRATION", "LEVERAGE", "MARGIN_UTILIZATION", "MARKET_FRESHNESS_SECONDS");
        assertThat(result.limitBreaches()).extracting(item -> item.limitCode()).containsExactly("SINGLE_INSTRUMENT_CONCENTRATION", "MARGIN_UTILIZATION");
    }

    @Test
    void marksStaleMarketDataWithoutUsingHostTimezone() {
        var result = service.calculate("ACC_ALPHA_01", data.positions("ACC_ALPHA_01", SampleDataRepository.DATA_AS_OF).positions(), new BigDecimal("70000000"), new BigDecimal("20000000"),
                SampleDataRepository.DATA_AS_OF, Instant.parse("2026-08-07T07:01:01Z"),
                new RiskCalculationService.Limits(new BigDecimal("180000000"), new BigDecimal("80000000"), new BigDecimal("0.40"), new BigDecimal("2.50"), new BigDecimal("0.70"), 60));
        assertThat(result.limitBreaches()).extracting(item -> item.limitCode()).contains("MARKET_FRESHNESS_SECONDS");
    }
}
