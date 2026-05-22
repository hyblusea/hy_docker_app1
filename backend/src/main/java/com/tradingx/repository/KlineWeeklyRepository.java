package com.tradingx.repository;

import com.tradingx.model.KlineWeeklyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface KlineWeeklyRepository extends JpaRepository<KlineWeeklyEntity, Long> {
    
    List<KlineWeeklyEntity> findByTsCodeOrderByTradeDateAsc(String tsCode);

    List<KlineWeeklyEntity> findByTsCodeAndTradeDateBetweenOrderByTradeDateAsc(String tsCode, String startDate, String endDate);
    
    @Query("SELECT MIN(k.tradeDate) FROM KlineWeeklyEntity k WHERE k.tsCode = :tsCode")
    String findFirstSyncDate(@Param("tsCode") String tsCode);
    
    @Query("SELECT MAX(k.tradeDate) FROM KlineWeeklyEntity k WHERE k.tsCode = :tsCode")
    String findLastSyncDate(@Param("tsCode") String tsCode);
    
    @Query("SELECT MIN(k.tradeDate) FROM KlineWeeklyEntity k")
    String findGlobalStartDate();
    
    @Query("SELECT MAX(k.tradeDate) FROM KlineWeeklyEntity k")
    String findGlobalEndDate();
    
    @Query("SELECT COUNT(k) FROM KlineWeeklyEntity k")
    Long countAllRecords();
    
    @Query("SELECT COUNT(DISTINCT k.tsCode) FROM KlineWeeklyEntity k")
    Integer countDistinctStocks();

    @Modifying
    @Transactional
    void deleteByTsCode(String tsCode);

    @Modifying
    @Transactional
    @Query("DELETE FROM KlineWeeklyEntity")
    void deleteAllBy();
}
