package com.tradingx.repository;

import com.tradingx.model.KlineSyncStatusEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface KlineSyncStatusRepository extends JpaRepository<KlineSyncStatusEntity, Long> {

    Optional<KlineSyncStatusEntity> findByTsCodeAndPeriod(String tsCode, String period);

    List<KlineSyncStatusEntity> findByPeriodOrderByTsCode(String period);

    Page<KlineSyncStatusEntity> findByPeriodOrderByTsCode(String period, Pageable pageable);

    Page<KlineSyncStatusEntity> findAllByOrderByTsCode(Pageable pageable);

    List<KlineSyncStatusEntity> findByStatus(String status);

    List<KlineSyncStatusEntity> findByStatusInOrderByTsCode(List<String> statuses);

    @Query("SELECT s FROM KlineSyncStatusEntity s WHERE s.status <> 'SUCCESS' ORDER BY s.tsCode")
    List<KlineSyncStatusEntity> findByStatusNotSuccess();

    @Query("SELECT s FROM KlineSyncStatusEntity s WHERE s.period = :period AND s.status <> 'SUCCESS' ORDER BY s.tsCode")
    List<KlineSyncStatusEntity> findIncompleteByPeriod(@Param("period") String period);

    long countByPeriodAndStatus(String period, String status);

    @Modifying
    @Transactional
    @Query("DELETE FROM KlineSyncStatusEntity")
    void deleteAllBy();

    @Query("SELECT s.period, MIN(s.lastSyncDate), MAX(s.lastSyncDate) FROM KlineSyncStatusEntity s WHERE s.status = 'SUCCESS' GROUP BY s.period")
    List<Object[]> findDateRangeByPeriod();

    @Query("SELECT COUNT(DISTINCT s.tsCode) FROM KlineSyncStatusEntity s WHERE s.period = :period AND s.status = 'SUCCESS'")
    long countSuccessByPeriod(@Param("period") String period);
}
