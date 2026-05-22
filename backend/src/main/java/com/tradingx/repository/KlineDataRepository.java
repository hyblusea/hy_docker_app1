package com.tradingx.repository;

import com.tradingx.model.KlineDataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface KlineDataRepository extends JpaRepository<KlineDataEntity, Long> {

    List<KlineDataEntity> findByTsCodeAndPeriodOrderByTradeDateAsc(String tsCode, String period);

    List<KlineDataEntity> findByTsCodeAndPeriodAndTradeDateBetweenOrderByTradeDateAsc(
            String tsCode, String period, String startDate, String endDate);

    @Query("SELECT MAX(k.tradeDate) FROM KlineDataEntity k WHERE k.tsCode = :tsCode AND k.period = :period")
    String findLastSyncDate(@Param("tsCode") String tsCode, @Param("period") String period);

    @Query("SELECT MIN(k.tradeDate) FROM KlineDataEntity k WHERE k.tsCode = :tsCode AND k.period = :period")
    String findFirstSyncDate(@Param("tsCode") String tsCode, @Param("period") String period);

    long countByTsCodeAndPeriod(String tsCode, String period);

    @Modifying
    @Transactional
    @Query("DELETE FROM KlineDataEntity k WHERE k.tsCode = :tsCode AND k.period = :period")
    void deleteByTsCodeAndPeriod(@Param("tsCode") String tsCode, @Param("period") String period);

    @Query("SELECT COUNT(DISTINCT k.tsCode) FROM KlineDataEntity k WHERE k.period = :period")
    long countDistinctTsCodeByPeriod(@Param("period") String period);

    @Query("SELECT COUNT(DISTINCT k.tradeDate) FROM KlineDataEntity k WHERE k.period = :period")
    long countDistinctTradeDateByPeriod(@Param("period") String period);

    @Query("SELECT k.period, COUNT(k) FROM KlineDataEntity k GROUP BY k.period")
    List<Object[]> countByPeriod();

    @Query("SELECT k.period, MIN(k.tradeDate), MAX(k.tradeDate) FROM KlineDataEntity k GROUP BY k.period")
    List<Object[]> findDateRangeByPeriod();
}
