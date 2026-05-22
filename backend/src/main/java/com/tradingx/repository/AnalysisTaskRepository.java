package com.tradingx.repository;

import com.tradingx.model.AnalysisTaskEntity;
import com.tradingx.model.AnalysisTaskSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface AnalysisTaskRepository extends JpaRepository<AnalysisTaskEntity, Long> {

    AnalysisTaskEntity findByTaskId(String taskId);

    List<AnalysisTaskEntity> findByUsernameOrderByCreatedAtDesc(String username);

    @Query("SELECT new com.tradingx.model.AnalysisTaskSummary(a.id, a.taskId, a.username, a.startDate, a.endDate, a.strategyCount, a.totalStocks, a.completed, a.initError, a.createdAt, a.updatedAt) FROM AnalysisTaskEntity a WHERE a.username = :username ORDER BY a.createdAt DESC")
    List<AnalysisTaskSummary> findSummariesByUsername(@Param("username") String username);

    @Modifying
    @Transactional
    @Query("DELETE FROM AnalysisTaskEntity a WHERE a.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
