package com.tradingx.repository;

import com.tradingx.model.StockListEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface StockListRepository extends JpaRepository<StockListEntity, Long> {

    Optional<StockListEntity> findByStockCode(String stockCode);

    List<StockListEntity> findByStockNameContainingOrStockCodeContainingOrSymbolPinyinContainingIgnoreCase(
            String name, String code, String pinyin);

    @Query("SELECT s FROM StockListEntity s WHERE " +
            "UPPER(s.stockCode) LIKE UPPER(CONCAT('%', :keyword, '%')) " +
            "OR s.stockName LIKE CONCAT('%', :keyword, '%') " +
            "OR UPPER(s.symbolPinyin) LIKE UPPER(CONCAT('%', :keyword, '%'))")
    List<StockListEntity> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT s FROM StockListEntity s WHERE s.stockName IS NOT NULL AND s.stockName <> '' AND s.stockName NOT LIKE '%ST%'")
    List<StockListEntity> findNonSTStocks();

    @Modifying
    @Transactional
    @Query("DELETE FROM StockListEntity")
    void deleteAllFast();
}
