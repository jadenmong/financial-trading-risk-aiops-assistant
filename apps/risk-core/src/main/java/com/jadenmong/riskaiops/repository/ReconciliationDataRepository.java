package com.jadenmong.riskaiops.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReconciliationDataRepository {
    record OrderData(String orderId, BigDecimal quantity, BigDecimal price, String status, String currency) {}
    record ExecutionData(String executionId, String orderId, BigDecimal quantity, BigDecimal price, String status, String currency) {}
    List<OrderData> orders(String accountId, LocalDate tradeDate);
    List<ExecutionData> executions(String accountId, LocalDate tradeDate);
}
