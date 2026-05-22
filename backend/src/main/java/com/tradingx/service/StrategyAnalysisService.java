package com.tradingx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingx.model.*;
import com.tradingx.repository.AnalysisTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class StrategyAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StrategyAnalysisService.class);

    private final StrategyService strategyService;
    private final StockService stockService;
    private final BacktestService backtestService;
    private final AnalysisTaskRepository analysisTaskRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StrategyAnalysisService(StrategyService strategyService, StockService stockService,
                                   BacktestService backtestService, AnalysisTaskRepository analysisTaskRepository) {
        this.strategyService = strategyService;
        this.stockService = stockService;
        this.backtestService = backtestService;
        this.analysisTaskRepository = analysisTaskRepository;
    }

    private static final int POOL_SIZE = 200;
    private static final long TASK_TIMEOUT_MINUTES = 30;

    private final ConcurrentHashMap<String, AnalysisTask> tasks = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupScheduler;
    private ExecutorService workExecutor;

    @jakarta.annotation.PostConstruct
    public void init() {
        workExecutor = new ThreadPoolExecutor(
                POOL_SIZE, POOL_SIZE, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "analysis-worker");
                    t.setDaemon(true);
                    return t;
                });

        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "analysis-task-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(this::cleanupStaleTasks,
                1, 1, TimeUnit.MINUTES);
        log.info("策略分析服务已启动, 工作线程池大小={}", POOL_SIZE);
    }

    @jakarta.annotation.PreDestroy
    public void destroy() {
        if (workExecutor != null) {
            workExecutor.shutdownNow();
        }
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
    }

    public String startAnalysis(StrategyAnalysisRequest request) {
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        AnalysisTask task = new AnalysisTask();
        task.taskId = taskId;
        task.total = new AtomicInteger(0);
        task.completed = new AtomicInteger(0);
        task.cancelled = new AtomicBoolean(false);
        task.completedFlag = new AtomicBoolean(false);
        task.currentStrategy = new AtomicReference<>("加载策略列表...");
        task.currentStock = new AtomicReference<>("");
        task.lastPollTime = System.currentTimeMillis();
        task.request = request;
        task.results = new ConcurrentHashMap<>();

        tasks.put(taskId, task);

        Thread.ofVirtual().start(() -> {
            try {
                List<Strategy> strategies;
                if (request.getStrategyIds() != null && !request.getStrategyIds().isEmpty()) {
                    strategies = request.getStrategyIds().stream()
                            .map(id -> {
                                try { return strategyService.getById(id); }
                                catch (Exception e) { return null; }
                            })
                            .filter(Objects::nonNull)
                            .toList();
                } else {
                    strategies = strategyService.listValid();
                }

                List<StockBasic> stocks = stockService.getNonSTStockList();

                task.total.set(strategies.size() * stocks.size());
                task.currentStrategy.set("");

                AnalysisTaskEntity taskEntity = new AnalysisTaskEntity();
                taskEntity.setTaskId(taskId);
                taskEntity.setUsername(request.getUsername());
                taskEntity.setStartDate(request.getStartDate());
                taskEntity.setEndDate(request.getEndDate());
                taskEntity.setStrategyCount(strategies.size());
                taskEntity.setTotalStocks(stocks.size());
                taskEntity.setCompleted(false);
                analysisTaskRepository.save(taskEntity);

                runAnalysis(task, strategies, stocks, request);
            } catch (Exception e) {
                log.error("策略分析初始化失败: taskId={}, error={}", taskId, e.getMessage());
                task.initError = e.getMessage();
                task.completedFlag.set(true);
                task.currentStrategy.set("");
                AnalysisTaskEntity entity = analysisTaskRepository.findByTaskId(taskId);
                if (entity != null) {
                    entity.setCompleted(true);
                    entity.setInitError(e.getMessage());
                    analysisTaskRepository.save(entity);
                }
            }
        });

        return taskId;
    }

    private void runAnalysis(AnalysisTask task, List<Strategy> strategies,
                             List<StockBasic> stocks, StrategyAnalysisRequest request) {

        for (Strategy strategy : strategies) {
            task.results.put(strategy.getId(), new ConcurrentHashMap<>());
        }

        Map<Long, Strategy> strategyMap = new HashMap<>();
        for (Strategy s : strategies) {
            strategyMap.put(s.getId(), s);
        }

        // ★ 预建 tsCode -> stockName 映射，避免后续反复 stream 查找
        Map<String, String> stockNameMap = new HashMap<>(stocks.size());
        for (StockBasic s : stocks) {
            stockNameMap.put(s.getTsCode(), s.getName());
        }

        // ========== 阶段一：加载行情数据 ==========
        task.currentStrategy.set("加载行情数据...");

        ConcurrentHashMap<String, List<DailyQuote>> quotesMap = new ConcurrentHashMap<>();
        AtomicInteger quotesLoaded = new AtomicInteger(0);

        List<CompletableFuture<Void>> loadFutures = new ArrayList<>();
        for (int i = 0; i < stocks.size(); i++) {
            final int stockIdx = i;
            loadFutures.add(CompletableFuture.runAsync(() -> {
                if (task.cancelled.get()) return;
                StockBasic stock = stocks.get(stockIdx);
                try {
                    DailyQuery query = new DailyQuery();
                    query.setTsCode(stock.getTsCode());
                    query.setStartDate(request.getStartDate());
                    query.setEndDate(request.getEndDate());
                    List<DailyQuote> quotes = stockService.queryDaily(query);
                    if (quotes != null && !quotes.isEmpty()) {
                        quotesMap.put(stock.getTsCode(), quotes);
                    }
                } catch (Exception e) {
                    log.warn("股票 {} 行情查询失败: {}", stock.getTsCode(), e.getMessage());
                } finally {
                    int done = quotesLoaded.incrementAndGet();
                    if (done % 500 == 0) {
                        log.info("行情加载进度: {}/{}", done, stocks.size());
                    }
                }
            }, workExecutor));
        }
        CompletableFuture.allOf(loadFutures.toArray(new CompletableFuture[0])).join();

        // ★ 行情加载完毕后检查是否已取消，避免无谓进入回测阶段
        if (task.cancelled.get()) {
            task.completedFlag.set(true);
            task.currentStrategy.set("");
            log.info("任务在行情加载后被取消: taskId={}", task.taskId);
            saveResultsToDb(task);
            return;
        }

        log.info("行情加载完成: taskId={}, 有效股票={}/{}", task.taskId, quotesMap.size(), stocks.size());

        // ========== 阶段二：回测计算 ==========
        task.currentStrategy.set("回测计算中...");

        int validStocks = quotesMap.size();
        task.total.set(validStocks * strategies.size());
        task.completed.set(0);

        List<CompletableFuture<Void>> backtestFutures = new ArrayList<>();
        for (Map.Entry<String, List<DailyQuote>> entry : quotesMap.entrySet()) {
            String tsCode = entry.getKey();
            List<DailyQuote> quotes = entry.getValue();
            // ★ 用预建的 Map 直接查找，O(1) 替代原来的 stream O(n)
            String stockName = stockNameMap.getOrDefault(tsCode, tsCode);

            for (Strategy strategy : strategies) {
                Long strategyId = strategy.getId();
                String strategyName = strategy.getName();

                backtestFutures.add(CompletableFuture.runAsync(() -> {
                    if (task.cancelled.get()) return;

                    ConcurrentHashMap<String, StockResult> stockResults = task.results.get(strategyId);

                    try {
                        task.currentStrategy.set(strategyName);
                        task.currentStock.set(stockName + "(" + tsCode + ")");

                        Strategy strategyEntity = strategyMap.get(strategyId);
                        BacktestResult result = backtestService.runBacktest(strategyEntity, quotes);

                        StockResult sr = new StockResult();
                        sr.tsCode = tsCode;
                        sr.stockName = stockName;
                        sr.totalReturn = result.getTotalReturn();
                        sr.winRate = result.getWinRate();
                        sr.tradeCount = result.getTradeCount();
                        sr.maxDrawdown = result.getMaxDrawdown();
                        sr.profitLoss = result.getProfitLoss();
                        sr.backtestResult = result;

                        stockResults.put(tsCode, sr);
                    } catch (Exception e) {
                        log.warn("策略 {} 回测失败: stock={}, error={}", strategyId, tsCode, e.getMessage());
                    } finally {
                        task.completed.incrementAndGet();
                    }
                }, workExecutor));
            }
        }
        CompletableFuture.allOf(backtestFutures.toArray(new CompletableFuture[0])).join();

        task.completedFlag.set(true);
        task.currentStrategy.set("");
        task.currentStock.set("");

        saveResultsToDb(task);

        log.info("策略分析完成: taskId={}, 策略数={}, 有效股票={}", task.taskId, task.results.size(), validStocks);
    }

    private void saveResultsToDb(AnalysisTask task) {
        try {
            List<StrategyAnalysisResult> results = buildResults(task.results, true);
            String resultsJson = objectMapper.writeValueAsString(results);

            AnalysisTaskEntity entity = analysisTaskRepository.findByTaskId(task.taskId);
            if (entity != null) {
                entity.setCompleted(true);
                entity.setResultsJson(resultsJson);
                analysisTaskRepository.save(entity);
            }
        } catch (Exception e) {
            log.error("保存策略分析结果到数据库失败: taskId={}", task.taskId, e);
        }
    }

    public StrategyAnalysisProgress getProgress(String taskId) {
        AnalysisTask task = tasks.get(taskId);
        if (task == null) return null;

        task.lastPollTime = System.currentTimeMillis();

        StrategyAnalysisProgress progress = new StrategyAnalysisProgress();
        progress.setTaskId(task.taskId);
        progress.setCurrent(task.completed.get());
        progress.setTotal(task.total.get());
        progress.setCurrentStrategy(task.currentStrategy.get() != null ? task.currentStrategy.get() : "");
        progress.setCurrentStock(task.currentStock.get() != null ? task.currentStock.get() : "");
        progress.setCompleted(task.completedFlag.get());
        progress.setCancelled(task.cancelled.get());
        progress.setStrategyCompleted(task.results.size());
        if (task.initError != null) progress.setInitError(task.initError);
        return progress;
    }

    public List<StrategyAnalysisResult> getResults(String taskId) {
        AnalysisTask task = tasks.get(taskId);
        if (task != null) {
            return buildResults(task.results, false);
        }

        AnalysisTaskEntity entity = analysisTaskRepository.findByTaskId(taskId);
        if (entity != null && entity.getResultsJson() != null) {
            try {
                return objectMapper.readValue(entity.getResultsJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, StrategyAnalysisResult.class));
            } catch (JsonProcessingException e) {
                log.error("解析策略分析结果失败: taskId={}", taskId, e);
            }
        }

        return null;
    }

    /**
     * ★ 核心修改：只统计有实际交易（tradeCount > 0）的股票，
     *   确保 盈利数 + 亏损数 == 股票数，亏损不再是简单的 "总数 - 盈利数"
     */
    private List<StrategyAnalysisResult> buildResults(
            ConcurrentHashMap<Long, ConcurrentHashMap<String, StockResult>> taskResults,
            boolean includeBacktest) {

        List<StrategyAnalysisResult> results = new ArrayList<>();

        for (Map.Entry<Long, ConcurrentHashMap<String, StockResult>> entry : taskResults.entrySet()) {
            Long strategyId = entry.getKey();
            Map<String, StockResult> stockResults = entry.getValue();

            Strategy strategy = strategyService.getById(strategyId);

            StrategyAnalysisResult result = new StrategyAnalysisResult();
            result.setStrategyId(strategyId);
            result.setStrategyName(strategy != null ? strategy.getName() : "未知策略");

            // ★ 关键：过滤掉没有产生交易的股票（tradeCount == 0）
            //   这些股票不参与盈亏统计，避免把"无交易"也算作"亏损"
            List<StockResult> tradedStocks = stockResults.values().stream()
                    .filter(sr -> sr.tradeCount > 0)
                    .collect(Collectors.toList());

            result.setTotalStocks(tradedStocks.size());

            int profitable = 0;
            int losing = 0;
            double totalReturn = 0;
            // ★ 修复原代码 Double.MIN_VALUE 的 bug（它是最小正数，不是最小负数）
            double maxReturn = Double.NEGATIVE_INFINITY;
            String maxReturnStock = "";
            double minReturn = Double.POSITIVE_INFINITY;
            String minReturnStock = "";
            double totalWinRate = 0;
            double totalMaxDrawdown = 0;
            int totalTrades = 0;
            double totalProfit = 0;
            double totalLoss = 0;

            List<StrategyAnalysisResult.StockPerformance> allStocks = new ArrayList<>();

            for (StockResult sr : tradedStocks) {
                if (sr.totalReturn > 0) {
                    profitable++;
                } else {
                    losing++;
                }

                totalReturn += sr.totalReturn;
                totalWinRate += sr.winRate;
                totalMaxDrawdown += sr.maxDrawdown;
                totalTrades += sr.tradeCount;

                if (sr.profitLoss > 0) totalProfit += sr.profitLoss;
                else totalLoss += Math.abs(sr.profitLoss);

                if (sr.totalReturn > maxReturn) {
                    maxReturn = sr.totalReturn;
                    maxReturnStock = sr.stockName + "(" + sr.tsCode + ")";
                }
                if (sr.totalReturn < minReturn) {
                    minReturn = sr.totalReturn;
                    minReturnStock = sr.stockName + "(" + sr.tsCode + ")";
                }

                StrategyAnalysisResult.StockPerformance sp = new StrategyAnalysisResult.StockPerformance();
                sp.setTsCode(sr.tsCode);
                sp.setStockName(sr.stockName);
                sp.setTotalReturn(sr.totalReturn);
                sp.setWinRate(sr.winRate);
                sp.setTradeCount(sr.tradeCount);
                sp.setMaxDrawdown(sr.maxDrawdown);
                if (includeBacktest) {
                    sp.setBacktestResult(sr.backtestResult);
                }
                allStocks.add(sp);
            }

            int count = tradedStocks.size();
            result.setProfitableStocks(profitable);
            result.setLosingStocks(losing);
            result.setAvgReturn(count > 0 ? totalReturn / count : 0);
            result.setMaxReturn(count > 0 ? maxReturn : 0);
            result.setMaxReturnStock(maxReturnStock);
            result.setMinReturn(count > 0 ? minReturn : 0);
            result.setMinReturnStock(minReturnStock);
            result.setAvgWinRate(count > 0 ? totalWinRate / count : 0);
            result.setAvgMaxDrawdown(count > 0 ? totalMaxDrawdown / count : 0);
            result.setTotalTrades(totalTrades);
            result.setAvgTradeCount(count > 0 ? (double) totalTrades / count : 0);
            result.setProfitFactor(totalLoss > 0 ? totalProfit / totalLoss : totalProfit > 0 ? Double.MAX_VALUE : 0);
            result.setTotalProfit(totalProfit);
            result.setTotalLoss(totalLoss);

            allStocks.sort((a, b) -> Double.compare(b.getTotalReturn(), a.getTotalReturn()));
            result.setTopStocks(allStocks.stream().limit(50).toList());

            List<StrategyAnalysisResult.StockPerformance> worstStocks = new ArrayList<>(allStocks);
            worstStocks.sort((a, b) -> Double.compare(a.getTotalReturn(), b.getTotalReturn()));
            result.setWorstStocks(worstStocks.stream().limit(50).toList());

            results.add(result);
        }

        results.sort((a, b) -> Double.compare(b.getAvgReturn(), a.getAvgReturn()));
        return results;
    }

    public boolean cancelTask(String taskId) {
        AnalysisTask task = tasks.get(taskId);
        if (task == null) return false;
        task.cancelled.set(true);
        return true;
    }

    private void cleanupStaleTasks() {
        try {
            long now = System.currentTimeMillis();
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, AnalysisTask> entry : tasks.entrySet()) {
                AnalysisTask task = entry.getValue();
                if (task.completedFlag.get() && (now - task.lastPollTime > 10 * 60 * 1000)) {
                    toRemove.add(entry.getKey());
                } else if (!task.completedFlag.get() && (now - task.lastPollTime > TASK_TIMEOUT_MINUTES * 60 * 1000)) {
                    task.cancelled.set(true);
                    task.completedFlag.set(true);
                    AnalysisTaskEntity entity = analysisTaskRepository.findByTaskId(task.taskId);
                    if (entity != null) {
                        entity.setCompleted(true);
                        analysisTaskRepository.save(entity);
                    }
                }
            }
            for (String taskId : toRemove) {
                tasks.remove(taskId);
            }
        } catch (Exception e) {
            log.error("清理分析任务异常", e);
        }
    }

    public BacktestResult getStockBacktestResult(String taskId, Long strategyId, String tsCode) {
        AnalysisTask task = tasks.get(taskId);
        if (task != null) {
            ConcurrentHashMap<String, StockResult> stockResults = task.results.get(strategyId);
            if (stockResults != null) {
                StockResult sr = stockResults.get(tsCode);
                if (sr != null) return sr.backtestResult;
            }
        }

        AnalysisTaskEntity entity = analysisTaskRepository.findByTaskId(taskId);
        if (entity != null && entity.getResultsJson() != null) {
            try {
                List<StrategyAnalysisResult> allResults = objectMapper.readValue(entity.getResultsJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, StrategyAnalysisResult.class));
                for (StrategyAnalysisResult r : allResults) {
                    if (strategyId.equals(r.getStrategyId())) {
                        BacktestResult br = findBacktestInStocks(r.getTopStocks(), tsCode);
                        if (br != null) return br;
                        br = findBacktestInStocks(r.getWorstStocks(), tsCode);
                        if (br != null) return br;
                    }
                }
            } catch (JsonProcessingException e) {
                log.error("解析策略分析结果失败: taskId={}", taskId, e);
            }
        }

        return null;
    }

    private BacktestResult findBacktestInStocks(List<StrategyAnalysisResult.StockPerformance> stocks, String tsCode) {
        if (stocks == null) return null;
        for (StrategyAnalysisResult.StockPerformance sp : stocks) {
            if (tsCode.equals(sp.getTsCode())) {
                return sp.getBacktestResult();
            }
        }
        return null;
    }

    public List<AnalysisTaskSummary> getHistoryTasks(String username) {
        return analysisTaskRepository.findSummariesByUsername(username);
    }

    public String getTaskOwner(String taskId) {
        AnalysisTaskEntity entity = analysisTaskRepository.findByTaskId(taskId);
        return entity != null ? entity.getUsername() : null;
    }

    @Transactional
    public boolean deleteTask(String taskId) {
        AnalysisTaskEntity entity = analysisTaskRepository.findByTaskId(taskId);
        if (entity == null) {
            return false;
        }
        analysisTaskRepository.delete(entity);
        tasks.remove(taskId);
        log.info("已删除策略分析任务: taskId={}", taskId);
        return true;
    }

    private static class AnalysisTask {
        String taskId;
        AtomicInteger total;
        AtomicInteger completed;
        AtomicBoolean cancelled;
        AtomicReference<String> currentStrategy;
        AtomicReference<String> currentStock;
        AtomicBoolean completedFlag;
        volatile String initError;
        volatile long lastPollTime;
        StrategyAnalysisRequest request;
        ConcurrentHashMap<Long, ConcurrentHashMap<String, StockResult>> results;
    }

    private static class StockResult {
        String tsCode;
        String stockName;
        double totalReturn;
        double winRate;
        int tradeCount;
        double maxDrawdown;
        double profitLoss;
        BacktestResult backtestResult;
    }
}
