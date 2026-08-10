package com.jadenmong.riskaiops.service;

import static java.math.RoundingMode.HALF_EVEN;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.jadenmong.riskaiops.domain.ReconciliationResult;
import com.jadenmong.riskaiops.repository.ReconciliationDataRepository.ExecutionData;
import com.jadenmong.riskaiops.repository.ReconciliationDataRepository.OrderData;

@Service
public class ReconciliationService {
    public ReconciliationResult reconcile(String accountId, String tradeDate, List<OrderData> orders, List<ExecutionData> rawExecutions) {
        List<ReconciliationResult.Difference> differences = new ArrayList<>();
        Map<String, OrderData> byOrder = new LinkedHashMap<>(); orders.forEach(order -> byOrder.put(order.orderId(), order));
        Map<String, ExecutionData> uniqueExecutions = new LinkedHashMap<>();
        for (ExecutionData execution : rawExecutions) {
            if (uniqueExecutions.putIfAbsent(execution.executionId(), execution) != null) {
                differences.add(diff("DUPLICATE_EXECUTION", "CRITICAL", execution.orderId(), execution.executionId(), "1", "2", execution.currency()));
            }
        }
        for (ExecutionData execution : uniqueExecutions.values()) {
            if (!byOrder.containsKey(execution.orderId())) differences.add(diff("ORPHAN_EXECUTION", "CRITICAL", execution.orderId(), execution.executionId(), null, d(execution.quantity()), execution.currency()));
        }
        for (OrderData order : orders) {
            List<ExecutionData> executions = uniqueExecutions.values().stream().filter(execution -> execution.orderId().equals(order.orderId())).toList();
            if (executions.isEmpty()) {
                differences.add(diff("MISSING_EXECUTION", "CRITICAL", order.orderId(), null, d(order.quantity()), "0.0000000000", order.currency()));
                continue;
            }
            BigDecimal totalQuantity = executions.stream().map(ExecutionData::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalQuantity.compareTo(order.quantity()) != 0) differences.add(diff("QUANTITY_MISMATCH", "WARNING", order.orderId(), null, d(order.quantity()), d(totalQuantity), order.currency()));
            BigDecimal notional = executions.stream().map(execution -> execution.quantity().multiply(execution.price())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal averagePrice = notional.divide(totalQuantity, 10, HALF_EVEN);
            if (averagePrice.subtract(order.price()).abs().compareTo(new BigDecimal("0.0100000000")) > 0) differences.add(diff("PRICE_MISMATCH", "WARNING", order.orderId(), null, d(order.price()), d(averagePrice), order.currency()));
            String brokerStatus = totalQuantity.compareTo(order.quantity()) >= 0 ? "FILLED" : "PARTIALLY_FILLED";
            if (!order.status().equals(brokerStatus)) differences.add(diff("STATUS_MISMATCH", "WARNING", order.orderId(), null, order.status(), brokerStatus, order.currency()));
            executions.stream().filter(execution -> !execution.currency().equals(order.currency())).forEach(execution -> differences.add(diff("CURRENCY_MISMATCH", "CRITICAL", order.orderId(), execution.executionId(), order.currency(), execution.currency(), execution.currency())));
        }
        Map<String, Integer> summary = new HashMap<>();
        summary.put("orders", orders.size()); summary.put("executions", uniqueExecutions.size()); summary.put("differences", differences.size());
        summary.put("critical", (int) differences.stream().filter(item -> item.severity().equals("CRITICAL")).count());
        summary.put("warning", (int) differences.stream().filter(item -> item.severity().equals("WARNING")).count());
        return new ReconciliationResult(accountId, tradeDate, summary, differences);
    }

    public ReconciliationResult reference(String accountId, String tradeDate) {
        List<OrderData> orders = List.of(
                new OrderData("OMS-A-1001", d0("1000"), d0("10"), "FILLED", "CNY"),
                new OrderData("OMS-A-1002", d0("500"), d0("20"), "FILLED", "CNY"));
        List<ExecutionData> executions = List.of(
                new ExecutionData("BRK-E-1", "OMS-A-1001", d0("500"), d0("10.05"), "FILLED", "CNY"),
                new ExecutionData("BRK-E-2", "OMS-A-1001", d0("400"), d0("10.05"), "FILLED", "USD"),
                new ExecutionData("BRK-E-2", "OMS-A-1001", d0("400"), d0("10.05"), "FILLED", "USD"),
                new ExecutionData("BRK-E-9", "OMS-UNKNOWN", d0("200"), d0("30"), "FILLED", "CNY"));
        return reconcile(accountId, tradeDate, orders, executions);
    }

    private static ReconciliationResult.Difference diff(String type, String severity, String orderId, String executionId, String expected, String actual, String currency) {
        return new ReconciliationResult.Difference(type, severity, orderId, executionId, expected, actual, currency);
    }
    private static String d(BigDecimal value) { return value.setScale(10, HALF_EVEN).toPlainString(); }
    private static BigDecimal d0(String value) { return new BigDecimal(value); }
}
