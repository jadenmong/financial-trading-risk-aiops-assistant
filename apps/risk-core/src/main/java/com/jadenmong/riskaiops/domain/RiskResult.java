package com.jadenmong.riskaiops.domain;

import java.time.Instant;
import java.util.List;

public record RiskResult(
        String accountId,
        String currency,
        Instant asOf,
        String grossExposure,
        String netExposure,
        String equityMarketValue,
        String futuresNotional,
        String deltaEquivalentExposure,
        String unrealizedPnl,
        String marginUsed,
        String leverage,
        String marginUtilization,
        String maxConcentration,
        List<PositionResult> positions,
        List<LimitCheck> limitChecks,
        List<LimitCheck> limitBreaches) {
    public record PositionResult(String instrumentId, String assetClass, String side, String quantity,
                                 String marketValue, String notional, String deltaEquivalentExposure,
                                 String unrealizedPnl, String marginUsed) {}
    public record LimitCheck(String limitCode, String actual, String limit, String status, String severity) {}
}
