
package com.tradingx.repository.prediction;

import com.tradingx.model.prediction.ModelVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelVersionRepository extends JpaRepository<ModelVersionEntity, Long> {

    List<ModelVersionEntity> findAllByOrderByCreatedAtDesc();

    List<ModelVersionEntity> findByModelNameOrderByCreatedAtDesc(String modelName);
}
