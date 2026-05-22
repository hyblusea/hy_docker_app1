package com.tradingx.controller;

import com.tradingx.model.*;
import com.tradingx.service.BacktestService;
import com.tradingx.service.StockService;
import com.tradingx.service.StrategyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/backtest")
public class BacktestController {

    private static final Logger log = LoggerFactory.getLogger(BacktestController.class);

    private final BacktestService backtestService;
    private final StockService stockService;
    private final StrategyService strategyService;

    // IO密集型任务，线程数可以开大一些；CPU密集型则保持在核心数左右
    private final ExecutorService backtestPool = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2)
    );

    public BacktestController(BacktestService backtestService, StockService stockService, StrategyService strategyService) {
        this.backtestService = backtestService;
        this.stockService = stockService;
        this.strategyService = strategyService;
    }

    @PostMapping
    public R<BacktestResult> runBacktest(@RequestBody BacktestRequest request) {
        return R.ok(backtestService.runBacktest(request.getStrategyId(), request.getQuotes()));
    }

    @PostMapping("/batch")
    public R<List<StrategyBacktestResult>> runBatchBacktest(@RequestBody BatchBacktestRequest request) {
        if (request.getTsCode() == null || request.getTsCode().isBlank()) {
            return R.fail("股票代码不能为空");
        }
        if (request.getStrategyIds() == null || request.getStrategyIds().isEmpty()) {
            return R.fail("请选择至少一个策略");
        }

        DailyQuery query = new DailyQuery();
        query.setTsCode(request.getTsCode());
        query.setStartDate(request.getStartDate());
        query.setEndDate(request.getEndDate());
        List<DailyQuote> quotes = stockService.queryDaily(query);

        if (quotes == null || quotes.isEmpty()) {
            return R.fail("未找到该股票的行情数据");
        }

        // ---- 并行提交所有回测任务 ----
        List<CompletableFuture<StrategyBacktestResult>> futures = new ArrayList<>();
        for (Long strategyId : request.getStrategyIds()) {
            CompletableFuture<StrategyBacktestResult> future = CompletableFuture.supplyAsync(() -> {
                StrategyBacktestResult item = new StrategyBacktestResult();
                item.setStrategyId(strategyId);
                try {
                    Strategy strategy = strategyService.getById(strategyId);
                    item.setStrategyName(strategy.getName());
                    BacktestResult btResult = backtestService.runBacktest(strategy, quotes);
                    item.setResult(btResult);
                } catch (Exception e) {
                    log.warn("策略回测失败: strategyId={}, error={}", strategyId, e.getMessage());
                    item.setStrategyName("策略" + strategyId);
                    BacktestResult errResult = new BacktestResult();
                    errResult.setTradeCount(0);
                    errResult.setTotalReturn(0);
                    errResult.setProfitLoss(0);
                    errResult.setWinRate(0);
                    errResult.setSignals(List.of());
                    item.setResult(errResult);
                }
                return item;
            }, backtestPool);
            futures.add(future);
        }

        // ---- 等待全部完成，按提交顺序收集结果 ----
        List<StrategyBacktestResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        return R.ok(results);
    }
}
