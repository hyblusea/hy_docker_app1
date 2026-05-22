package com.tradingx.repository;

import com.tradingx.model.KlineMonthlyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface KlineMonthlyRepository extends JpaRepository<KlineMonthlyEntity, Long> {
    
    List<KlineMonthlyEntity> findByTsCodeOrderByTradeDateAsc(String tsCode);

    List<KlineMonthlyEntity> findByTsCodeAndTradeDateBetweenOrderByTradeDateAsc(String tsCode, String startDate, String endDate);
    
    @Query("SELECT MIN(k.tradeDate) FROM KlineMonthlyEntity k WHERE k.tsCode = :tsCode")
    String findFirstSyncDate(@Param("tsCode") String tsCode);
    
    @Query("SELECT MAX(k.tradeDate) FROM KlineMonthlyEntity k WHERE k.tsCode = :tsCode")
    String findLastSyncDate(@Param("tsCode") String tsCode);
    
    @Query("SELECT MIN(k.tradeDate) FROM KlineMonthlyEntity k")
    String findGlobalStartDate();
    
    @Query("SELECT MAX(k.tradeDate) FROM KlineMonthlyEntity k")
    String findGlobalEndDate();
    
    @Query("SELECT COUNT(k) FROM KlineMonthlyEntity k")
    Long countAllRecords();
    
    @Query("SELECT COUNT(DISTINCT k.tsCode) FROM KlineMonthlyEntity k")
    Integer countDistinctStocks();

    @Modifying
    @Transactional
    void deleteByTsCode(String tsCode);

    @Modifying
    @Transactional
    @Query("DELETE FROM KlineMonthlyEntity")
    void deleteAllBy();
}
