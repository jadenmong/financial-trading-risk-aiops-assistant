package com.jadenmong.riskaiops.repository;

import static com.jadenmong.riskaiops.domain.Position.AssetClass.EQUITY;
import static com.jadenmong.riskaiops.domain.Position.AssetClass.INDEX_FUTURE;
import static com.jadenmong.riskaiops.domain.Position.Side.LONG;
import static com.jadenmong.riskaiops.domain.Position.Side.SHORT;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import com.jadenmong.riskaiops.domain.Position;

@Repository
@ConditionalOnProperty(name = "app.reference-mode", havingValue = "true")
public class SampleDataRepository implements RiskDataRepository {
    public static final Instant DATA_AS_OF = Instant.parse("2026-08-07T07:00:00Z");
    public static final String DATA_VERSION = "2026-08-07.v1";

    private static final Map<String, MarketSnapshot> MARKETS = Map.of(
            "SSE:600519", market("SSE:600519", "600519", "SSE", "1412.0000000000", "1438.5000000000", "1408.0000000000", "1431.2500000000", "1409.8000000000", "2865400.0000000000"),
            "SSE:601318", market("SSE:601318", "601318", "SSE", "58.1000000000", "59.2000000000", "57.9000000000", "58.8800000000", "58.0000000000", "58000000.0000000000"),
            "SSE:600036", market("SSE:600036", "600036", "SSE", "45.2000000000", "46.1000000000", "45.0000000000", "45.8600000000", "45.1000000000", "42000000.0000000000"),
            "SZSE:000001", market("SZSE:000001", "000001", "SZSE", "11.4200000000", "11.6800000000", "11.3500000000", "11.5800000000", "11.4100000000", "93000000.0000000000"),
            "SZSE:000858", market("SZSE:000858", "000858", "SZSE", "138.0000000000", "140.2000000000", "137.5000000000", "139.3000000000", "137.9000000000", "9100000.0000000000"),
            "SZSE:300750", market("SZSE:300750", "300750", "SZSE", "228.0000000000", "234.0000000000", "226.5000000000", "232.6000000000", "227.8000000000", "26500000.0000000000"),
            "CFFEX:IF2608", future("CFFEX:IF2608", "IF2608", "4006.4000000000", "3978.0000000000"),
            "CFFEX:IC2608", future("CFFEX:IC2608", "IC2608", "6238.2000000000", "6201.4000000000")
    );

    @Override
    public MarketSnapshot marketSnapshot(String instrumentId, Instant asOf) {
        MarketSnapshot snapshot = MARKETS.get(instrumentId);
        return snapshot != null && !snapshot.observedAt().isAfter(asOf) ? snapshot : null;
    }

    @Override
    public PositionSet positions(String accountId, Instant asOf) {
        if (!List.of("ACC_ALPHA_01", "ACC_ALPHA_02", "ACC_BETA_01", "ACC_BETA_02").contains(accountId) || DATA_AS_OF.isAfter(asOf)) return new PositionSet(List.of(), DATA_AS_OF, DATA_VERSION);
        return new PositionSet(List.of(
                new Position("SSE:600519", EQUITY, LONG, d("30000"), d("1400"), d("1431.25"), null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, "CNY"),
                new Position("SSE:601318", EQUITY, LONG, d("200000"), d("55"), d("58.88"), null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, "CNY"),
                new Position("CFFEX:IF2608", INDEX_FUTURE, SHORT, d("100"), d("4010"), d("4002.8"), d("4010"), d("300"), BigDecimal.ONE, d("0.12"), "CNY")
        ), DATA_AS_OF, DATA_VERSION);
    }

    private static MarketSnapshot market(String id, String symbol, String venue, String open, String high, String low, String close, String prevClose, String volume) {
        return new MarketSnapshot(id, symbol, venue, "EQUITY", "CNY", open, high, low, close, prevClose,
                new BigDecimal(close).subtract(d("0.05")).toPlainString(), new BigDecimal(close).add(d("0.05")).toPlainString(), volume, DATA_AS_OF, DATA_VERSION, List.of());
    }

    private static MarketSnapshot future(String id, String symbol, String close, String prevClose) {
        return new MarketSnapshot(id, symbol, "CFFEX", "INDEX_FUTURE", "CNY", prevClose,
                new BigDecimal(close).add(d("12.4")).toPlainString(), new BigDecimal(close).subtract(d("15.8")).toPlainString(), close, prevClose,
                new BigDecimal(close).subtract(d("0.2")).toPlainString(), new BigDecimal(close).add(d("0.2")).toPlainString(), "82210.0000000000", DATA_AS_OF, DATA_VERSION, List.of());
    }

    private static BigDecimal d(String value) { return new BigDecimal(value); }
}
