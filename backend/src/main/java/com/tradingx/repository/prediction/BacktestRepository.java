
package com.tradingx.repository.prediction;

import com.tradingx.model.prediction.BacktestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BacktestRepository extends JpaRepository<BacktestEntity, Long> {

    List<BacktestEntity> findByModelVersionIdOrderByCreatedAtDesc(Long modelVersionId);

    List<BacktestEntity> findByStatusOrderByCreatedAtDesc(String status);
}

