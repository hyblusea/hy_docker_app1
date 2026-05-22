package com.tradingx.repository;

import com.tradingx.model.TrackStockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackStockRepository extends JpaRepository<TrackStockEntity, Long> {

    List<TrackStockEntity> findByUsernameOrderByCreatedAtDesc(String username);

    Optional<TrackStockEntity> findByUsernameAndTsCodeAndStrategyId(String username, String tsCode, Long strategyId);

    boolean existsByUsernameAndTsCodeAndStrategyId(String username, String tsCode, Long strategyId);

    void deleteByIdAndUsername(Long id, String username);
}
