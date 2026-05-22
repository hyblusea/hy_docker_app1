package com.tradingx.controller;

import com.tradingx.model.*;
import com.tradingx.service.KlineSyncService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kline")
@RequireAdmin
public class KlineSyncController {

    private final KlineSyncService klineSyncService;

    public KlineSyncController(KlineSyncService klineSyncService) {
        this.klineSyncService = klineSyncService;
    }

    @GetMapping("/dashboard")
    public R<KlineDashboard> getDashboard() {
        return R.ok(klineSyncService.getDashboard());
    }

    @GetMapping("/sync/status")
    public R<List<KlineSyncStatusEntity>> getSyncStatus(@RequestParam(required = false) String period) {
        return R.ok(klineSyncService.getSyncStatus(period));
    }

    @GetMapping("/sync/status/paged")
    public R<Page<KlineSyncStatusEntity>> getSyncStatusPaged(
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(klineSyncService.getSyncStatusPaged(period, page, size));
    }

    @PostMapping("/sync/full")
    public R<String> startFullSync(@RequestBody KlineSyncRequest request) {
        String taskId = klineSyncService.startFullSync(request);
        return R.ok(taskId);
    }

    @GetMapping("/sync/progress/{taskId}")
    public R<KlineSyncProgress> getSyncProgress(@PathVariable String taskId) {
        KlineSyncProgress progress = klineSyncService.getProgress(taskId);
        if (progress == null) {
            return R.fail("任务不存在");
        }
        return R.ok(progress);
    }

    @PostMapping("/sync/cancel/{taskId}")
    public R<Void> cancelSync(@PathVariable String taskId) {
        klineSyncService.cancelTask(taskId);
        return R.ok(null);
    }

    @GetMapping("/missing")
    public R<MissingDataResult> getMissingDataStats() {
        return R.ok(klineSyncService.getMissingDataStats());
    }

    @GetMapping("/range")
    public R<Map<String, KlineRangeInfo>> getGlobalDateRange() {
        return R.ok(klineSyncService.getGlobalDateRange());
    }

    @PostMapping("/sync/fill")
    public R<String> fillMissingData() {
        String taskId = klineSyncService.fillMissingData();
        if (taskId == null) {
            return R.ok("无需补全");
        }
        return R.ok(taskId);
    }
}
