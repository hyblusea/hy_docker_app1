package com.tradingx.controller;

import com.tradingx.model.*;
import com.tradingx.service.StockScreenService;
import com.tradingx.service.StockService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;
    private final StockScreenService stockScreenService;

    public StockController(StockService stockService, StockScreenService stockScreenService) {
        this.stockService = stockService;
        this.stockScreenService = stockScreenService;
    }

    @GetMapping("/list")
    public R<List<StockBasic>> getStockList() {
        return R.ok(stockService.getStockList());
    }

    @GetMapping("/search")
    public R<List<StockBasic>> searchStock(@RequestParam String keyword) {
        return R.ok(stockService.searchStock(keyword));
    }

    @PostMapping("/daily")
    public R<List<DailyQuote>> queryDaily(@RequestBody DailyQuery query) {
        return R.ok(stockService.queryDaily(query));
    }

    @PostMapping("/screen")
    public R<String> startScreen(@RequestBody StockScreenRequest request) {
        String taskId = stockScreenService.startScreen(request);
        return R.ok(taskId);
    }

    @GetMapping("/screen/{taskId}/progress")
    public R<StockScreenProgress> getScreenProgress(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session, false)) {
            return R.fail("无权访问该任务");
        }
        StockScreenProgress progress = stockScreenService.getProgress(taskId);
        if (progress == null) {
            return R.fail("任务不存在");
        }
        return R.ok(progress);
    }

    @GetMapping("/screen/{taskId}/result")
    public R<List<StockScreenMatch>> getScreenResult(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session, false)) {
            return R.fail("无权访问该任务");
        }
        List<StockScreenMatch> results = stockScreenService.getResults(taskId);
        if (results == null) {
            return R.fail("任务不存在");
        }
        return R.ok(results);
    }

    @GetMapping("/screen/{taskId}/list")
    public R<List<StockScreenMatch>> getScreenList(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session, false)) {
            return R.fail("无权访问该任务");
        }
        List<StockScreenMatch> list = stockScreenService.getList(taskId);
        if (list == null) {
            return R.fail("任务不存在");
        }
        return R.ok(list);
    }

    @GetMapping("/screen/{taskId}/detail")
    public R<StockScreenMatch> getScreenDetail(
            @PathVariable String taskId,
            @RequestParam String tsCode,
            @RequestParam Long strategyId,
            HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session, false)) {
            return R.fail("无权访问该任务");
        }
        StockScreenMatch detail = stockScreenService.getMatchDetail(taskId, tsCode, strategyId);
        if (detail == null) {
            return R.fail("匹配记录不存在");
        }
        return R.ok(detail);
    }

    @PostMapping("/screen/{taskId}/cancel")
    public R<Void> cancelScreen(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session, true)) {
            return R.fail("无权操作该任务");
        }
        stockScreenService.cancelTask(taskId);
        return R.ok(null);
    }

    @GetMapping("/screen/history")
    public R<List<ScreenTaskEntity>> getScreenHistory(@RequestParam String username) {
        return R.ok(stockScreenService.getHistoryTasks(username));
    }

    @DeleteMapping("/screen/{taskId}")
    public R<Void> deleteScreenTask(@PathVariable String taskId, HttpSession session) {
        if (!isTaskOwnerOrRoot(taskId, session, true)) {
            return R.fail("无权删除该任务");
        }
        boolean deleted = stockScreenService.deleteTask(taskId);
        if (!deleted) {
            return R.fail("任务不存在");
        }
        return R.ok(null);
    }

    @PostMapping("/refresh")
    public R<Void> refreshStockList(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null || !"root".equals(user.getRole())) {
            return R.fail("需要管理员权限");
        }
        stockService.refreshStockList();
        return R.ok(null);
    }

    private boolean isTaskOwnerOrRoot(String taskId, HttpSession session, boolean requireOwner) {
        User user = (User) session.getAttribute("user");
        if (user == null) return false;
        if ("root".equals(user.getRole())) return true;
        String taskOwner = stockScreenService.getTaskOwner(taskId);
        return user.getUsername().equals(taskOwner);
    }
}
