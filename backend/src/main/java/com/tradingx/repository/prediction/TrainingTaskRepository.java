
package com.tradingx.repository.prediction;

import com.tradingx.model.prediction.TrainingTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingTaskRepository extends JpaRepository<TrainingTaskEntity, Long> {

    List<TrainingTaskEntity> findAllByOrderByStartTimeDesc();

    List<TrainingTaskEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<TrainingTaskEntity> findBySymbolOrderByStartTimeDesc(String symbol);
}
