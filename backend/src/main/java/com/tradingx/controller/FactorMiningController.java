package com.tradingx.controller;

import com.tradingx.model.*;
import com.tradingx.service.FactorCalcService;
import com.tradingx.service.FactorStrategyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/factor")
public class FactorMiningController {

    private final FactorCalcService factorCalcService;
    private final FactorStrategyService factorStrategyService;

    public FactorMiningController(FactorCalcService factorCalcService, FactorStrategyService factorStrategyService) {
        this.factorCalcService = factorCalcService;
        this.factorStrategyService = factorStrategyService;
    }

    @PostMapping("/eval")
    public R<String> startEval(@RequestBody FactorEvalRequest request) {
        String taskId = factorCalcService.startCalcAndEval(request);
        return R.ok(taskId);
    }

    @GetMapping("/progress/{taskId}")
    public R<FactorEvalProgress> getProgress(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session)) {
            return R.fail("无权访问该任务");
        }
        FactorEvalProgress progress = factorCalcService.getProgress(taskId);
        if (progress == null) {
            return R.fail("任务不存在");
        }
        return R.ok(progress);
    }

    @GetMapping("/results/{taskId}")
    public R<List<FactorEvalResult>> getResults(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session)) {
            return R.fail("无权访问该任务");
        }
        List<FactorEvalResult> results = factorCalcService.getResults(taskId);
        if (results == null) {
            return R.fail("任务不存在");
        }
        return R.ok(results);
    }

    @PostMapping("/cancel/{taskId}")
    public R<Void> cancelTask(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session)) {
            return R.fail("无权操作该任务");
        }
        factorCalcService.cancelTask(taskId);
        return R.ok(null);
    }

    @GetMapping("/history")
    public R<List<FactorEvalTaskEntity>> getHistory(@RequestParam String username) {
        return R.ok(factorCalcService.getHistoryTasks(username));
    }

    @DeleteMapping("/{taskId}")
    public R<Void> deleteTask(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session)) {
            return R.fail("无权删除该任务");
        }
        boolean deleted = factorCalcService.deleteTask(taskId);
        if (!deleted) {
            return R.fail("任务不存在");
        }
        return R.ok(null);
    }

    @GetMapping("/definitions")
    public R<List<FactorDefinition>> getFactorDefinitions() {
        return R.ok(factorCalcService.getFactorDefinitions());
    }

    @PostMapping("/generate-strategy")
    public R<Strategy> generateStrategy(@RequestBody Map<String, Object> body, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return R.fail("未登录");

        @SuppressWarnings("unchecked")
        List<String> factorNames = (List<String>) body.get("factor_names");
        String taskId = (String) body.get("task_id");
        String combinationMethod = (String) body.getOrDefault("combination_method", "rule");

        if (factorNames == null || factorNames.isEmpty()) {
            return R.fail("请选择至少一个因子");
        }

        List<FactorEvalResult> allResults;
        if (taskId != null && !taskId.isBlank()) {
            allResults = factorCalcService.getResults(taskId);
        } else {
            return R.fail("缺少任务ID");
        }

        if (allResults == null || allResults.isEmpty()) {
            return R.fail("未找到评估结果");
        }

        List<FactorEvalResult> selected = allResults.stream()
                .filter(r -> factorNames.contains(r.getFactorName()))
                .toList();

        if (selected.isEmpty()) {
            return R.fail("所选因子不在评估结果中");
        }

        try {
            Strategy strategy;
            if ("scoring".equals(combinationMethod)) {
                strategy = factorStrategyService.generateScoringStrategy(selected, taskId, user.getUsername());
            } else {
                strategy = factorStrategyService.generateStrategy(selected, user.getUsername());
            }
            return R.ok(strategy);
        } catch (Exception e) {
            return R.fail("策略生成失败: " + e.getMessage());
        }
    }

    private boolean isTaskOwnerOrRoot(String taskId, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return false;
        if ("root".equals(user.getRole())) return true;
        String taskOwner = factorCalcService.getTaskOwner(taskId);
        return user.getUsername().equals(taskOwner);
    }
}
