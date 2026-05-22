package com.tradingx.repository;

import com.tradingx.model.ScreenTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScreenTaskRepository extends JpaRepository<ScreenTaskEntity, Long> {

    Optional<ScreenTaskEntity> findByTaskId(String taskId);

    List<ScreenTaskEntity> findByUsernameOrderByCreatedAtDesc(String username);

    @Transactional
    void deleteAllBy();

    long count();

    @Modifying
    @Transactional
    @Query("DELETE FROM ScreenTaskEntity s WHERE s.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT s.taskId FROM ScreenTaskEntity s WHERE s.createdAt < :cutoff")
    List<String> findTaskIdsByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
