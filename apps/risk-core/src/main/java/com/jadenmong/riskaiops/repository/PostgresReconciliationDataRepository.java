package com.jadenmong.riskaiops.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "app.reference-mode", havingValue = "false", matchIfMissing = true)
public class PostgresReconciliationDataRepository implements ReconciliationDataRepository {
    private final RiskQueryMapper mapper;
    public PostgresReconciliationDataRepository(RiskQueryMapper mapper) { this.mapper = mapper; }
    @Override public List<OrderData> orders(String accountId, LocalDate tradeDate) { return mapper.orders(accountId, tradeDate); }
    @Override public List<ExecutionData> executions(String accountId, LocalDate tradeDate) { return mapper.executions(accountId, tradeDate); }
}
