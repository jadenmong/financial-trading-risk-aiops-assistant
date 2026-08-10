package com.jadenmong.riskaiops.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.reference-mode", havingValue = "true")
public class SampleReconciliationDataRepository implements ReconciliationDataRepository {
    @Override
    public List<OrderData> orders(String accountId, LocalDate tradeDate) {
        return List.of(new OrderData("OMS-A-1001", d("1000"), d("10"), "FILLED", "CNY"), new OrderData("OMS-A-1002", d("500"), d("20"), "FILLED", "CNY"));
    }
    @Override
    public List<ExecutionData> executions(String accountId, LocalDate tradeDate) {
        return List.of(
                new ExecutionData("BRK-E-1", "OMS-A-1001", d("500"), d("10.05"), "FILLED", "CNY"),
                new ExecutionData("BRK-E-2", "OMS-A-1001", d("400"), d("10.05"), "FILLED", "USD"),
                new ExecutionData("BRK-E-2", "OMS-A-1001", d("400"), d("10.05"), "FILLED", "USD"),
                new ExecutionData("BRK-E-9", "OMS-UNKNOWN", d("200"), d("30"), "FILLED", "CNY"));
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
}
