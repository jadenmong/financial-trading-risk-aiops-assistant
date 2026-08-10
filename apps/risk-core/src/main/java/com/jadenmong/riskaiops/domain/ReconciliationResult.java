package com.jadenmong.riskaiops.domain;

import java.util.List;
import java.util.Map;

public record ReconciliationResult(String accountId, String tradeDate, Map<String, Integer> summary, List<Difference> differences) {
    public record Difference(String type, String severity, String orderId, String executionId,
                             String expected, String actual, String currency) {}
}
