package com.tradingx.service;

import com.tradingx.model.KlineSyncStatusEntity;
import com.tradingx.repository.KlineSyncStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
public class DatabaseDiagnosticTest {

    @Autowired
    private KlineSyncStatusRepository klineSyncStatusRepository;

    @Test
    public void checkSyncStatusData() {
        System.out.println("\n========== 检查同步状态表数据 ==========\n");
        
        List<KlineSyncStatusEntity> allStatus = klineSyncStatusRepository.findAll();
        
        // 按周期分组统计
        Map<String, Long> countByPeriod = allStatus.stream()
                .collect(Collectors.groupingBy(KlineSyncStatusEntity::getPeriod, Collectors.counting()));
        
        System.out.println("各周期记录数:");
        countByPeriod.forEach((period, count) -> System.out.println("  " + period + ": " + count));
        
        // 检查9开头股票
        System.out.println("\n9开头股票:");
        allStatus.stream()
                .filter(s -> s.getTsCode().startsWith("9"))
                .forEach(s -> System.out.printf("  %s: %s, 记录数=%d\n", 
                        s.getTsCode(), s.getStatus(), s.getTotalRecords()));
        
        // 检查状态分布
        Map<String, Long> countByStatus = allStatus.stream()
                .collect(Collectors.groupingBy(KlineSyncStatusEntity::getStatus, Collectors.counting()));
        
        System.out.println("\n状态分布:");
        countByStatus.forEach((status, count) -> System.out.println("  " + status + ": " + count));
        
        // 检查日期范围
        System.out.println("\n日期范围 (SUCCESS 状态):");
        allStatus.stream()
                .filter(s -> "SUCCESS".equals(s.getStatus()))
                .collect(Collectors.groupingBy(KlineSyncStatusEntity::getPeriod))
                .forEach((period, list) -> {
                    String minDate = list.stream()
                            .map(KlineSyncStatusEntity::getLastSyncDate)
                            .min(String::compareTo)
                            .orElse("-");
                    String maxDate = list.stream()
                            .map(KlineSyncStatusEntity::getLastSyncDate)
                            .max(String::compareTo)
                            .orElse("-");
                    System.out.println("  " + period + ": " + minDate + " ~ " + maxDate);
                });
    }
}
