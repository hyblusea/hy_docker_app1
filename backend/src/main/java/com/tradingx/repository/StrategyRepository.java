package com.tradingx.repository;

import com.tradingx.model.Strategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StrategyRepository extends JpaRepository<Strategy, Long> {
    @Query("SELECT s FROM Strategy s WHERE s.valid = true OR s.valid IS NULL")
    List<Strategy> findValidStrategies();

    List<Strategy> findByCreatedBy(String createdBy);

    @Query("SELECT s FROM Strategy s WHERE s.createdBy = :username OR s.createdByRole = 'root'")
    List<Strategy> findVisibleByUser(@Param("username") String username);
}
