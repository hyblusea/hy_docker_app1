package com.tradingx.repository;

import com.tradingx.model.FactorEvalTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FactorEvalTaskRepository extends JpaRepository<FactorEvalTaskEntity, Long> {

    Optional<FactorEvalTaskEntity> findByTaskId(String taskId);

    List<FactorEvalTaskEntity> findByUsernameOrderByCreatedAtDesc(String username);
}
