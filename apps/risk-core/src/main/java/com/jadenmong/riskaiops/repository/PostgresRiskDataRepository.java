package com.jadenmong.riskaiops.repository;

import static java.math.RoundingMode.HALF_EVEN;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.jadenmong.riskaiops.domain.Position;

@Repository
@ConditionalOnProperty(name = "app.reference-mode", havingValue = "false", matchIfMissing = true)
public class PostgresRiskDataRepository implements RiskDataRepository {
    private final RiskQueryMapper mapper;
    public PostgresRiskDataRepository(RiskQueryMapper mapper) { this.mapper = mapper; }

    @Override
    public MarketSnapshot marketSnapshot(String instrumentId, Instant asOf) {
        var row = mapper.latestMarket(instrumentId, asOf);
        if (row == null) return null;
        return new MarketSnapshot(row.instrumentId(), row.symbol(), row.venue(), row.assetClass(), row.currency(),
                d(row.openPrice()), d(row.highPrice()), d(row.lowPrice()), d(row.closePrice()), d(row.previousClose()),
                d(row.bidPrice()), d(row.askPrice()), d(row.volume()), row.observedAt(), row.dataVersion(), List.of());
    }

    @Override
    public PositionSet positions(String accountId, Instant asOf) {
        List<RiskQueryMapper.PositionRow> rows = mapper.latestPositions(accountId, asOf);
        List<Position> positions = rows.stream().map(row -> new Position(row.instrumentId(), Position.AssetClass.valueOf(row.assetClass()),
                Position.Side.valueOf(row.side()), row.quantity(), row.averagePrice(), row.currentPrice(), row.previousSettlement(),
                row.contractMultiplier(), row.delta(), row.marginRate(), row.currency())).toList();
        Instant observed = rows.stream().map(RiskQueryMapper.PositionRow::observedAt).max(Instant::compareTo).orElse(asOf);
        return new PositionSet(positions, observed, "postgres-materialized-v1");
    }
    private static String d(BigDecimal value) { return value == null ? null : value.setScale(10, HALF_EVEN).toPlainString(); }
}
