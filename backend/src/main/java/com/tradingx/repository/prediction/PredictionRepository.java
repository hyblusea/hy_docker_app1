
package com.tradingx.repository.prediction;

import com.tradingx.model.prediction.PredictionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PredictionRepository extends JpaRepository<PredictionEntity, Long> {

    List<PredictionEntity> findByTsCodeOrderByCreatedAtDesc(String tsCode);
}
