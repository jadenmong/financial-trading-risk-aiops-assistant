package com.jadenmong.riskaiops.repository;

import java.time.Instant;
import java.util.List;

import com.jadenmong.riskaiops.domain.Position;

public interface RiskDataRepository {
    record MarketSnapshot(String instrumentId, String symbol, String venue, String assetClass, String currency,
                          String open, String high, String low, String close, String prevClose, String bid,
                          String ask, String volume, Instant observedAt, String dataVersion, List<String> qualityFlags) {}
    record PositionSet(List<Position> positions, Instant observedAt, String dataVersion) {}

    MarketSnapshot marketSnapshot(String instrumentId, Instant asOf);
    PositionSet positions(String accountId, Instant asOf);
}
