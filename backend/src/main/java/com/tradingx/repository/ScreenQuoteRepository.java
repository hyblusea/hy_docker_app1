package com.tradingx.repository;

import com.tradingx.model.ScreenQuoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ScreenQuoteRepository extends JpaRepository<ScreenQuoteEntity, Long> {

    List<ScreenQuoteEntity> findByTaskIdAndTsCode(String taskId, String tsCode);

    @Transactional
    void deleteByTaskId(String taskId);

    @Transactional
    void deleteAllBy();

    @Transactional
    void deleteByTaskIdIn(List<String> taskIds);
}
