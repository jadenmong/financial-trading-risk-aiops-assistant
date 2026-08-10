package com.jadenmong.riskaiops.service;

import static java.math.RoundingMode.HALF_EVEN;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.jadenmong.riskaiops.domain.Position;
import com.jadenmong.riskaiops.domain.RiskResult;

@Service
public class RiskCalculationService {
    private static final int SCALE = 10;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    public record Limits(BigDecimal gross, BigDecimal net, BigDecimal concentration, BigDecimal leverage,
                         BigDecimal marginUtilization, long freshnessSeconds) {}

    public RiskResult calculate(String accountId, List<Position> positions, BigDecimal netAssetValue,
                                BigDecimal marginCapacity, Instant dataAsOf, Instant requestedAsOf, Limits limits) {
        BigDecimal gross = ZERO, net = ZERO, equity = ZERO, futures = ZERO, deltaExposure = ZERO, pnl = ZERO, margin = ZERO, maxPosition = ZERO;
        List<RiskResult.PositionResult> details = new ArrayList<>();
        for (Position position : positions) {
            BigDecimal direction = BigDecimal.valueOf(position.direction());
            BigDecimal marketValue = ZERO, notional = ZERO, deltaEquivalent = ZERO, positionPnl, positionMargin = ZERO;
            if (position.assetClass() == Position.AssetClass.EQUITY) {
                marketValue = position.quantity().multiply(position.currentPrice()).setScale(SCALE, HALF_EVEN);
                deltaEquivalent = marketValue.multiply(direction).setScale(SCALE, HALF_EVEN);
                positionPnl = position.currentPrice().subtract(position.averagePrice()).multiply(position.quantity()).multiply(direction).setScale(SCALE, HALF_EVEN);
                equity = equity.add(marketValue);
                maxPosition = maxPosition.max(marketValue.abs());
            } else {
                notional = position.quantity().multiply(position.currentPrice()).multiply(position.contractMultiplier()).setScale(SCALE, HALF_EVEN);
                deltaEquivalent = notional.multiply(position.delta()).multiply(direction).setScale(SCALE, HALF_EVEN);
                positionPnl = position.currentPrice().subtract(position.previousSettlement()).multiply(position.quantity()).multiply(position.contractMultiplier()).multiply(direction).setScale(SCALE, HALF_EVEN);
                positionMargin = notional.abs().multiply(position.marginRate()).setScale(SCALE, HALF_EVEN);
                futures = futures.add(notional.abs());
            }
            gross = gross.add(deltaEquivalent.abs()); net = net.add(deltaEquivalent); deltaExposure = deltaExposure.add(deltaEquivalent);
            pnl = pnl.add(positionPnl); margin = margin.add(positionMargin);
            details.add(new RiskResult.PositionResult(position.instrumentId(), position.assetClass().name(), position.side().name(), d(position.quantity()), d(marketValue), d(notional), d(deltaEquivalent), d(positionPnl), d(positionMargin)));
        }
        BigDecimal concentration = ratio(maxPosition, netAssetValue);
        BigDecimal leverage = ratio(gross, netAssetValue);
        BigDecimal utilization = ratio(margin, marginCapacity);
        long freshness = Math.max(0, Duration.between(dataAsOf, requestedAsOf).toSeconds());
        List<RiskResult.LimitCheck> checks = List.of(
                check("GROSS_EXPOSURE", gross, limits.gross(), "CRITICAL"),
                check("NET_EXPOSURE", net.abs(), limits.net(), "CRITICAL"),
                check("SINGLE_INSTRUMENT_CONCENTRATION", concentration, limits.concentration(), "CRITICAL"),
                check("LEVERAGE", leverage, limits.leverage(), "WARNING"),
                check("MARGIN_UTILIZATION", utilization, limits.marginUtilization(), "CRITICAL"),
                check("MARKET_FRESHNESS_SECONDS", BigDecimal.valueOf(freshness), BigDecimal.valueOf(limits.freshnessSeconds()), "WARNING")
        );
        return new RiskResult(accountId, "CNY", requestedAsOf, d(gross), d(net), d(equity), d(futures), d(deltaExposure), d(pnl), d(margin), d(leverage), d(utilization), d(concentration), details, checks, checks.stream().filter(check -> check.status().equals("BREACH")).toList());
    }

    private static RiskResult.LimitCheck check(String code, BigDecimal actual, BigDecimal limit, String severity) {
        return new RiskResult.LimitCheck(code, d(actual), d(limit), actual.compareTo(limit) > 0 ? "BREACH" : "PASS", severity);
    }
    private static BigDecimal ratio(BigDecimal value, BigDecimal denominator) { return denominator.signum() == 0 ? ZERO : value.divide(denominator, SCALE, HALF_EVEN); }
    private static String d(BigDecimal value) { return value.setScale(SCALE, HALF_EVEN).toPlainString(); }
}
