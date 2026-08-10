package com.jadenmong.riskaiops.domain;

import java.math.BigDecimal;

public record Position(
        String instrumentId,
        AssetClass assetClass,
        Side side,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        BigDecimal previousSettlement,
        BigDecimal contractMultiplier,
        BigDecimal delta,
        BigDecimal marginRate,
        String currency) {
    public enum AssetClass { EQUITY, INDEX_FUTURE }
    public enum Side { LONG, SHORT }
    public int direction() { return side == Side.LONG ? 1 : -1; }
}
