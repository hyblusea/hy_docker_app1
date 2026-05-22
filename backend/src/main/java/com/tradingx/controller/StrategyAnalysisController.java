package com.tradingx.controller;

import com.tradingx.model.*;
import com.tradingx.service.StrategyAnalysisService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
public class StrategyAnalysisController {

    private final StrategyAnalysisService strategyAnalysisService;

    public StrategyAnalysisController(StrategyAnalysisService strategyAnalysisService) {
        this.strategyAnalysisService = strategyAnalysisService;
    }

    @PostMapping("/start")
    public R<String> startAnalysis(@RequestBody StrategyAnalysisRequest request) {
        String taskId = strategyAnalysisService.startAnalysis(request);
        return R.ok(taskId);
    }

    @GetMapping("/progress/{taskId}")
    public R<StrategyAnalysisProgress> getProgress(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session)) {
            return R.fail("无权访问该任务");
        }
        StrategyAnalysisProgress progress = strategyAnalysisService.getProgress(taskId);
        if (progress == null) {
            return R.fail("任务不存在");
        }
        return R.ok(progress);
    }

    @GetMapping("/results/{taskId}")
    public R<List<StrategyAnalysisResult>> getResults(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session)) {
            return R.fail("无权访问该任务");
        }
        List<StrategyAnalysisResult> results = strategyAnalysisService.getResults(taskId);
        if (results == null) {
            return R.fail("任务不存在");
        }
        return R.ok(results);
    }

    @GetMapping("/results/{taskId}/{strategyId}/{tsCode}")
    public R<BacktestResult> getStockBacktestResult(
            @PathVariable String taskId,
            @PathVariable Long strategyId,
            @PathVariable String tsCode,
            HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session)) {
            return R.fail("无权访问该任务");
        }
        BacktestResult result = strategyAnalysisService.getStockBacktestResult(taskId, strategyId, tsCode);
        if (result == null) {
            return R.fail("未找到回测结果");
        }
        return R.ok(result);
    }

    @PostMapping("/cancel/{taskId}")
    public R<Void> cancelAnalysis(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session)) {
            return R.fail("无权操作该任务");
        }
        strategyAnalysisService.cancelTask(taskId);
        return R.ok(null);
    }

    @GetMapping("/history")
    public R<List<AnalysisTaskSummary>> getHistory(@RequestParam String username) {
        return R.ok(strategyAnalysisService.getHistoryTasks(username));
    }

    @DeleteMapping("/{taskId}")
    public R<Void> deleteTask(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session)) {
            return R.fail("无权删除该任务");
        }
        boolean deleted = strategyAnalysisService.deleteTask(taskId);
        if (!deleted) {
            return R.fail("任务不存在");
        }
        return R.ok(null);
    }

    private boolean isTaskOwnerOrRoot(String taskId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return false;
        if ("root".equals(user.getRole())) return true;
        String taskOwner = strategyAnalysisService.getTaskOwner(taskId);
        return user.getUsername().equals(taskOwner);
    }
}
