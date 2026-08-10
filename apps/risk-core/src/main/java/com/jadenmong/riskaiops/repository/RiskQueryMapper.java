package com.jadenmong.riskaiops.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RiskQueryMapper {
    @Select("""
        SELECT m.instrument_id AS instrumentId, i.symbol, i.venue, i.asset_class AS assetClass, i.currency,
               m.open_price AS openPrice, m.high_price AS highPrice, m.low_price AS lowPrice,
               m.close_price AS closePrice, m.previous_close AS previousClose, m.bid_price AS bidPrice,
               m.ask_price AS askPrice, m.volume, m.observed_at AS observedAt, m.data_version AS dataVersion
          FROM trading.market_snapshot m JOIN reference.instrument i USING (instrument_id)
         WHERE m.instrument_id = #{instrumentId} AND m.observed_at <= #{asOf}
         ORDER BY m.observed_at DESC LIMIT 1
        """)
    MarketRow latestMarket(@Param("instrumentId") String instrumentId, @Param("asOf") Instant asOf);

    @Select("""
        SELECT DISTINCT ON (p.instrument_id) p.instrument_id AS instrumentId, i.asset_class AS assetClass,
               p.side, p.quantity, p.average_price AS averagePrice, p.current_price AS currentPrice,
               p.average_price AS previousSettlement, COALESCE(i.contract_multiplier, 1) AS contractMultiplier,
               1.0000000000 AS delta, COALESCE(i.margin_rate, 0) AS marginRate, i.currency,
               p.observed_at AS observedAt
          FROM trading.position_snapshot p JOIN reference.instrument i USING (instrument_id)
         WHERE p.account_id = #{accountId} AND p.observed_at <= #{asOf}
         ORDER BY p.instrument_id, p.observed_at DESC
        """)
    List<PositionRow> latestPositions(@Param("accountId") String accountId, @Param("asOf") Instant asOf);

    @Select("SELECT order_id AS orderId, quantity, price, status, currency FROM trading.oms_order WHERE account_id=#{accountId} AND trade_date=#{tradeDate} ORDER BY order_id")
    List<ReconciliationDataRepository.OrderData> orders(@Param("accountId") String accountId, @Param("tradeDate") java.time.LocalDate tradeDate);

    @Select("SELECT execution_id AS executionId, order_id AS orderId, quantity, price, status, currency FROM trading.broker_execution WHERE account_id=#{accountId} AND trade_date=#{tradeDate} ORDER BY row_id")
    List<ReconciliationDataRepository.ExecutionData> executions(@Param("accountId") String accountId, @Param("tradeDate") java.time.LocalDate tradeDate);

    record MarketRow(String instrumentId, String symbol, String venue, String assetClass, String currency,
                     BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice, BigDecimal closePrice,
                     BigDecimal previousClose, BigDecimal bidPrice, BigDecimal askPrice, BigDecimal volume,
                     Instant observedAt, String dataVersion) {}
    record PositionRow(String instrumentId, String assetClass, String side, BigDecimal quantity,
                       BigDecimal averagePrice, BigDecimal currentPrice, BigDecimal previousSettlement,
                       BigDecimal contractMultiplier, BigDecimal delta, BigDecimal marginRate,
                       String currency, Instant observedAt) {}
}
