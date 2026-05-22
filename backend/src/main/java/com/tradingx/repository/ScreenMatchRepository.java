package com.tradingx.repository;

import com.tradingx.model.ScreenMatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ScreenMatchRepository extends JpaRepository<ScreenMatchEntity, Long> {

    interface LightweightScreenMatch {
        String getTsCode();
        String getName();
        Long getStrategyId();
        String getStrategyName();
        Double getTotalReturn();
        Double getWinRate();
        Integer getTradeCount();
        Double getProfitLoss();
        Double getMaxDrawdown();
        Integer getOpenPositionCount();
        Double getInitialCapital();
        Double getFinalCapital();
        Double getTotalFees();
    }

    interface ScreenMatchListProjection {
        String getTsCode();
        String getName();
        Long getStrategyId();
        String getStrategyName();
        Double getTotalReturn();
        Double getWinRate();
        Integer getTradeCount();
        Double getProfitLoss();
        Integer getOpenPositionCount();
    }

    List<ScreenMatchEntity> findByTaskId(String taskId);

    @Query("SELECT m.tsCode AS tsCode, m.name AS name, m.strategyId AS strategyId, m.strategyName AS strategyName, " +
           "m.totalReturn AS totalReturn, m.winRate AS winRate, m.tradeCount AS tradeCount, " +
           "m.profitLoss AS profitLoss, m.maxDrawdown AS maxDrawdown, m.openPositionCount AS openPositionCount, " +
           "m.initialCapital AS initialCapital, m.finalCapital AS finalCapital, m.totalFees AS totalFees " +
           "FROM ScreenMatchEntity m WHERE m.taskId = :taskId")
    List<LightweightScreenMatch> findLightweightByTaskId(@Param("taskId") String taskId);

    @Query("SELECT m.tsCode AS tsCode, m.name AS name, m.strategyId AS strategyId, m.strategyName AS strategyName, " +
           "m.totalReturn AS totalReturn, m.winRate AS winRate, m.tradeCount AS tradeCount, " +
           "m.profitLoss AS profitLoss, m.openPositionCount AS openPositionCount " +
           "FROM ScreenMatchEntity m WHERE m.taskId = :taskId")
    List<ScreenMatchListProjection> findListByTaskId(@Param("taskId") String taskId);

    Optional<ScreenMatchEntity> findByTaskIdAndTsCodeAndStrategyId(String taskId, String tsCode, Long strategyId);

    @Transactional
    void deleteByTaskId(String taskId);

    @Transactional
    void deleteAllBy();

    @Transactional
    void deleteByTaskIdIn(List<String> taskIds);
}
