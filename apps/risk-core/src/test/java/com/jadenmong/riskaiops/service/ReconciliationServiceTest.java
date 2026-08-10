package com.jadenmong.riskaiops.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReconciliationServiceTest {
    @Test
    void classifiesAllFixedDifferenceFamilies() {
        var result = new ReconciliationService().reference("ACC_ALPHA_01", "2026-08-07");
        assertThat(result.differences()).extracting(item -> item.type()).contains(
                "MISSING_EXECUTION", "ORPHAN_EXECUTION", "DUPLICATE_EXECUTION", "QUANTITY_MISMATCH", "PRICE_MISMATCH", "STATUS_MISMATCH", "CURRENCY_MISMATCH");
        assertThat(result.summary().get("critical")).isGreaterThan(0);
    }
}
