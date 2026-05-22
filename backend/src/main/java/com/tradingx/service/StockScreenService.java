package com.tradingx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingx.model.*;
import com.tradingx.repository.ScreenMatchRepository;
import com.tradingx.repository.ScreenQuoteRepository;
import com.tradingx.repository.ScreenTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class StockScreenService {

    private static final Logger log = LoggerFactory.getLogger(StockScreenService.class);

    private final StockService stockService;
    private final BacktestService backtestService;
    private final StrategyService strategyService;
    private final KlineService klineService;
    private final ScreenTaskRepository screenTaskRepository;
    private final ScreenMatchRepository screenMatchRepository;
    private final ScreenQuoteRepository screenQuoteRepository;
    private final DataSource dataSource;
    private final StockScreenService self;

    public StockScreenService(StockService stockService, BacktestService backtestService,
                              StrategyService strategyService, KlineService klineService,
                              ScreenTaskRepository screenTaskRepository,
                              ScreenMatchRepository screenMatchRepository, ScreenQuoteRepository screenQuoteRepository,
                              DataSource dataSource,
                              @Lazy StockScreenService self) {
        this.stockService = stockService;
        this.backtestService = backtestService;
        this.strategyService = strategyService;
        this.klineService = klineService;
        this.screenTaskRepository = screenTaskRepository;
        this.screenMatchRepository = screenMatchRepository;
        this.screenQuoteRepository = screenQuoteRepository;
        this.dataSource = dataSource;
        this.self = self;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, ScreenTask> tasks = new ConcurrentHashMap<>();

    private static final int POOL_SIZE = 200;
    private static final long TASK_TIMEOUT_MINUTES = 2;
    private static final long CLEANUP_INTERVAL_MINUTES = 1;

    private ScheduledExecutorService cleanupScheduler;
    private ExecutorService workExecutor;

    @PostConstruct
    public void init() {
        workExecutor = new ThreadPoolExecutor(
                POOL_SIZE, POOL_SIZE, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "screen-worker");
                    t.setDaemon(true);
                    return t;
                });

        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "screen-task-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(this::cleanupStaleTasks,
                CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
        log.info("选股服务已启动, 工作线程池大小={}, 超时={}分钟", POOL_SIZE, TASK_TIMEOUT_MINUTES);
    }

    @PreDestroy
    public void destroy() {
        if (workExecutor != null) {
            workExecutor.shutdownNow();
        }
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
    }

    private void cleanupStaleTasks() {
        try {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, ScreenTask> entry : tasks.entrySet()) {
                ScreenTask task = entry.getValue();
                if (task.completedFlag.get()) {
                    continue;
                }
                long elapsed = now - task.lastPollTime;
                if (elapsed > TASK_TIMEOUT_MINUTES * 60 * 1000) {
                    log.warn("选股任务超时自动取消: taskId={}, 已有{}分钟无轮询", task.taskId, elapsed / 60000);
                    task.cancelled.set(true);
                    task.completedFlag.set(true);
                    screenTaskRepository.findByTaskId(task.taskId).ifPresent(entity -> {
                        entity.setCompleted(true);
                        entity.setMatchCount(task.matchCount.get());
                        screenTaskRepository.save(entity);
                    });
                }
            }

            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, ScreenTask> entry : tasks.entrySet()) {
                ScreenTask task = entry.getValue();
                if (task.completedFlag.get() && (now - task.lastPollTime > 2 * 60 * 1000)) {
                    toRemove.add(entry.getKey());
                }
            }
            for (String taskId : toRemove) {
                tasks.remove(taskId);
                log.info("清理已完成选股任务: taskId={}", taskId);
            }
        } catch (Exception e) {
            log.error("清理任务异常", e);
        }
    }

    public String startScreen(StockScreenRequest request) {
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        ScreenTask task = new ScreenTask();
        task.taskId = taskId;
        task.total = new AtomicInteger(0);
        task.processed = new AtomicInteger(0);
        task.completed = new AtomicInteger(0);
        task.matchCount = new AtomicInteger(0);
        task.cancelled = new AtomicBoolean(false);
        task.currentStock = new AtomicReference<>("加载股票列表...");
        task.completedFlag = new AtomicBoolean(false);
        task.initError = null;
        task.lastPollTime = System.currentTimeMillis();
        task.startDate = request.getStartDate();
        task.endDate = request.getEndDate();
        task.screenMode = request.getScreenMode();

        tasks.put(taskId, task);

        Thread.ofVirtual().start(() -> {
            try {
                List<StockBasic> stocks = stockService.getNonSTStockList();

                task.total.set(stocks.size());
                task.currentStock.set("");

                ScreenTaskEntity taskEntity = new ScreenTaskEntity();
                taskEntity.setTaskId(taskId);
                taskEntity.setUsername(request.getUsername());
                taskEntity.setStartDate(request.getStartDate());
                taskEntity.setEndDate(request.getEndDate());
                taskEntity.setTotalStocks(stocks.size());
                taskEntity.setMatchCount(0);
                taskEntity.setCompleted(false);
                screenTaskRepository.save(taskEntity);

                runScreenTask(task, stocks, request);
            } catch (Exception e) {
                log.error("选股任务初始化失败: taskId={}, error={}", taskId, e.getMessage());
                task.initError = e.getMessage();
                task.completedFlag.set(true);
                task.currentStock.set("");
                screenTaskRepository.findByTaskId(taskId).ifPresent(entity -> {
                    entity.setCompleted(true);
                    entity.setInitError(e.getMessage());
                    screenTaskRepository.save(entity);
                });
            }
        });

        return taskId;
    }

    private void runScreenTask(ScreenTask task, List<StockBasic> stocks, StockScreenRequest request) {
        Map<Long, Strategy> strategyMap = new HashMap<>();
        for (Long strategyId : request.getStrategyIds()) {
            try {
                Strategy s = strategyService.getById(strategyId);
                if (s != null) strategyMap.put(strategyId, s);
            } catch (Exception e) {
                log.warn("加载策略 {} 失败: {}", strategyId, e.getMessage());
            }
        }

        task.currentStock.set("加载行情数据...");

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

        log.info("行情加载完成: taskId={}, 有效股票={}/{}", task.taskId, quotesMap.size(), stocks.size());
        task.currentStock.set("回测筛选中...");

        int validStocks = quotesMap.size();
        task.total.set(validStocks * strategyMap.size());
        task.completed.set(0);

        List<CompletableFuture<Void>> backtestFutures = new ArrayList<>();
        for (Map.Entry<String, List<DailyQuote>> entry : quotesMap.entrySet()) {
            String tsCode = entry.getKey();
            List<DailyQuote> quotes = entry.getValue();
            String stockName = stocks.stream()
                    .filter(s -> s.getTsCode().equals(tsCode))
                    .map(StockBasic::getName)
                    .findFirst().orElse(tsCode);

            for (Long strategyId : request.getStrategyIds()) {
                backtestFutures.add(CompletableFuture.runAsync(() -> {
                    if (task.cancelled.get()) return;

                    try {
                        task.currentStock.set(stockName + "(" + tsCode + ")");

                        Strategy strategyEntity = strategyMap.get(strategyId);
                        BacktestResult result = backtestService.runBacktest(strategyEntity, quotes);

                        List<BacktestResult.BacktestSignal> signals = result.getSignals();
                        boolean matched = false;

                        if ("holding".equals(task.screenMode)) {
                            if (signals != null && !signals.isEmpty()) {
                                matched = "BUY".equals(signals.get(signals.size() - 1).getType());
                            }
                        } else {
                            List<BacktestResult.BacktestSignal> buySignals = signals.stream()
                                    .filter(s -> "BUY".equals(s.getType()))
                                    .toList();

                            if (!buySignals.isEmpty()) {
                                BacktestResult.BacktestSignal lastBuy = buySignals.get(buySignals.size() - 1);
                                String lastBuyDate = lastBuy.getTradeDate();
                                String lastQuoteDate = quotes.get(quotes.size() - 1).getTradeDate();
                                matched = lastBuyDate != null && lastBuyDate.equals(lastQuoteDate);
                            }
                        }

                        if (matched) {
                            StockScreenMatch match = new StockScreenMatch();
                            match.setTsCode(tsCode);
                            match.setName(stockName);
                            match.setStrategyId(strategyId);
                            match.setStrategyName(strategyEntity != null ? strategyEntity.getName() : "未知策略");
                            match.setQuotes(quotes);
                            match.setResult(result);

                            task.matchCount.incrementAndGet();
                            task.matchQueue.add(match);
                        }
                    } catch (Exception e) {
                        log.warn("策略 {} 回测失败: stock={}, error={}", strategyId, tsCode, e.getMessage());
                    } finally {
                        task.completed.incrementAndGet();
                    }
                }, workExecutor));
            }
        }
        CompletableFuture.allOf(backtestFutures.toArray(new CompletableFuture[0])).join();

        // 批量保存匹配结果到 DB
        List<StockScreenMatch> allMatches = new ArrayList<>();
        StockScreenMatch m;
        while ((m = task.matchQueue.poll()) != null) {
            allMatches.add(m);
        }
        task.cachedResults = allMatches;

        if (!allMatches.isEmpty()) {
            self.batchSaveMatches(task.taskId, allMatches);
        }

        task.completedFlag.set(true);
        task.currentStock.set("");

        screenTaskRepository.findByTaskId(task.taskId).ifPresent(entity -> {
            entity.setMatchCount(task.matchCount.get());
            entity.setCompleted(true);
            if (task.initError != null) {
                entity.setInitError(task.initError);
            }
            screenTaskRepository.save(entity);
        });

        log.info("选股任务完成: taskId={}, 匹配={}, 有效股票={}", task.taskId, task.matchCount.get(), validStocks);
    }

    @Transactional
    public void batchSaveMatches(String taskId, List<StockScreenMatch> matches) {
        List<ScreenMatchEntity> entities = new ArrayList<>(matches.size());
        for (StockScreenMatch match : matches) {
            try {
                ScreenMatchEntity matchEntity = new ScreenMatchEntity();
                matchEntity.setTaskId(taskId);
                matchEntity.setTsCode(match.getTsCode());
                matchEntity.setName(match.getName());
                matchEntity.setStrategyId(match.getStrategyId());
                matchEntity.setStrategyName(match.getStrategyName());

                BacktestResult result = match.getResult();
                matchEntity.setTotalReturn(result.getTotalReturn());
                matchEntity.setWinRate(result.getWinRate());
                matchEntity.setTradeCount(result.getTradeCount());
                matchEntity.setProfitLoss(result.getProfitLoss());
                matchEntity.setMaxDrawdown(result.getMaxDrawdown());
                matchEntity.setOpenPositionCount(result.getOpenPositionCount());
                matchEntity.setInitialCapital(result.getInitialCapital());
                matchEntity.setFinalCapital(result.getFinalCapital());
                matchEntity.setTotalFees(result.getTotalFees());

                try {
                    matchEntity.setSignalsJson(objectMapper.writeValueAsString(result.getSignals()));
                } catch (JsonProcessingException e) {
                    matchEntity.setSignalsJson("[]");
                }

                entities.add(matchEntity);
            } catch (Exception e) {
                log.error("构建匹配实体失败: taskId={}, tsCode={}", taskId, match.getTsCode(), e);
            }
        }
        screenMatchRepository.saveAll(entities);
    }

    @Transactional
    public void saveMatchToDb(String taskId, StockScreenMatch match) {
        try {
            ScreenMatchEntity matchEntity = new ScreenMatchEntity();
            matchEntity.setTaskId(taskId);
            matchEntity.setTsCode(match.getTsCode());
            matchEntity.setName(match.getName());
            matchEntity.setStrategyId(match.getStrategyId());
            matchEntity.setStrategyName(match.getStrategyName());

            BacktestResult result = match.getResult();
            matchEntity.setTotalReturn(result.getTotalReturn());
            matchEntity.setWinRate(result.getWinRate());
            matchEntity.setTradeCount(result.getTradeCount());
            matchEntity.setProfitLoss(result.getProfitLoss());
            matchEntity.setMaxDrawdown(result.getMaxDrawdown());
            matchEntity.setOpenPositionCount(result.getOpenPositionCount());
            matchEntity.setInitialCapital(result.getInitialCapital());
            matchEntity.setFinalCapital(result.getFinalCapital());
            matchEntity.setTotalFees(result.getTotalFees());

            try {
                matchEntity.setSignalsJson(objectMapper.writeValueAsString(result.getSignals()));
            } catch (JsonProcessingException e) {
                matchEntity.setSignalsJson("[]");
            }

            screenMatchRepository.save(matchEntity);
        } catch (Exception e) {
            log.error("保存选股匹配结果到数据库失败: taskId={}, tsCode={}", taskId, match.getTsCode(), e);
        }
    }

    public StockScreenMatch getMatchDetail(String taskId, String tsCode, Long strategyId) {
        ScreenTask task = tasks.get(taskId);
        if (task != null) {
            List<StockScreenMatch> source = task.cachedResults != null ? task.cachedResults : new ArrayList<>(task.matchQueue);
            for (StockScreenMatch m : source) {
                if (m.getTsCode().equals(tsCode) && m.getStrategyId().equals(strategyId)) {
                    StockScreenMatch detail = new StockScreenMatch();
                    detail.setTsCode(m.getTsCode());
                    detail.setName(m.getName());
                    detail.setStrategyId(m.getStrategyId());
                    detail.setStrategyName(m.getStrategyName());
                    detail.setQuotes(Collections.emptyList());
                    detail.setResult(m.getResult());
                    return detail;
                }
            }
        }

        Optional<ScreenMatchEntity> entityOpt = screenMatchRepository.findByTaskIdAndTsCodeAndStrategyId(taskId, tsCode, strategyId);
        if (entityOpt.isEmpty()) {
            return null;
        }

        ScreenMatchEntity me = entityOpt.get();
        StockScreenMatch match = new StockScreenMatch();
        match.setTsCode(me.getTsCode());
        match.setName(me.getName());
        match.setStrategyId(me.getStrategyId());
        match.setStrategyName(me.getStrategyName());
        match.setQuotes(Collections.emptyList());

        BacktestResult result = new BacktestResult();
        result.setTotalReturn(me.getTotalReturn());
        result.setWinRate(me.getWinRate());
        result.setTradeCount(me.getTradeCount());
        result.setProfitLoss(me.getProfitLoss());
        result.setMaxDrawdown(me.getMaxDrawdown());
        result.setOpenPositionCount(me.getOpenPositionCount());
        result.setInitialCapital(me.getInitialCapital());
        result.setFinalCapital(me.getFinalCapital());
        result.setTotalFees(me.getTotalFees());

        try {
            List<BacktestResult.BacktestSignal> signals = objectMapper.readValue(
                    me.getSignalsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, BacktestResult.BacktestSignal.class)
            );
            result.setSignals(signals);
        } catch (JsonProcessingException e) {
            result.setSignals(Collections.emptyList());
        }

        match.setResult(result);
        return match;
    }

    public StockScreenProgress getProgress(String taskId) {
        ScreenTask task = tasks.get(taskId);
        if (task == null) {
            return null;
        }

        task.lastPollTime = System.currentTimeMillis();

        StockScreenProgress progress = new StockScreenProgress();
        progress.setCurrent(task.completed.get());
        progress.setTotal(task.total.get());
        progress.setCurrentStock(task.currentStock.get() != null ? task.currentStock.get() : "");
        progress.setCompleted(task.completedFlag.get());
        progress.setCancelled(task.cancelled.get());
        progress.setMatchCount(task.matchCount.get());
        if (task.initError != null) {
            progress.setInitError(task.initError);
        }
        return progress;
    }

    public List<StockScreenMatch> getResults(String taskId) {
        ScreenTask task = tasks.get(taskId);
        if (task != null && task.cachedResults != null) {
            return toLightweightMatches(task.cachedResults);
        }
        if (task != null) {
            List<StockScreenMatch> fromQueue = new ArrayList<>();
            for (StockScreenMatch m : task.matchQueue) {
                fromQueue.add(m);
            }
            if (!fromQueue.isEmpty()) {
                return toLightweightMatches(fromQueue);
            }
        }
        return loadLightweightResultsFromDb(taskId);
    }

    public List<StockScreenMatch> getList(String taskId) {
        ScreenTask task = tasks.get(taskId);
        if (task != null && task.cachedResults != null) {
            return toListMatches(task.cachedResults);
        }
        if (task != null) {
            List<StockScreenMatch> fromQueue = new ArrayList<>();
            for (StockScreenMatch m : task.matchQueue) {
                fromQueue.add(m);
            }
            if (!fromQueue.isEmpty()) {
                return toListMatches(fromQueue);
            }
        }
        return loadListFromDb(taskId);
    }

    private List<StockScreenMatch> toListMatches(List<StockScreenMatch> fullMatches) {
        List<StockScreenMatch> light = new ArrayList<>(fullMatches.size());
        for (StockScreenMatch m : fullMatches) {
            StockScreenMatch lm = new StockScreenMatch();
            lm.setTsCode(m.getTsCode());
            lm.setName(m.getName());
            lm.setStrategyId(m.getStrategyId());
            lm.setStrategyName(m.getStrategyName());
            lm.setQuotes(Collections.emptyList());

            BacktestResult r = new BacktestResult();
            BacktestResult src = m.getResult();
            r.setTotalReturn(src.getTotalReturn());
            r.setWinRate(src.getWinRate());
            r.setTradeCount(src.getTradeCount());
            r.setProfitLoss(src.getProfitLoss());
            r.setOpenPositionCount(src.getOpenPositionCount());
            r.setSignals(Collections.emptyList());
            lm.setResult(r);

            light.add(lm);
        }
        return light;
    }

    private List<StockScreenMatch> loadListFromDb(String taskId) {
        Optional<ScreenTaskEntity> taskOpt = screenTaskRepository.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            return null;
        }

        List<ScreenMatchRepository.ScreenMatchListProjection> projections = screenMatchRepository.findListByTaskId(taskId);
        if (projections.isEmpty()) {
            return new ArrayList<>();
        }

        List<StockScreenMatch> results = new ArrayList<>();
        for (ScreenMatchRepository.ScreenMatchListProjection me : projections) {
            StockScreenMatch match = new StockScreenMatch();
            match.setTsCode(me.getTsCode());
            match.setName(me.getName());
            match.setStrategyId(me.getStrategyId());
            match.setStrategyName(me.getStrategyName());
            match.setQuotes(Collections.emptyList());

            BacktestResult result = new BacktestResult();
            result.setTotalReturn(me.getTotalReturn());
            result.setWinRate(me.getWinRate());
            result.setTradeCount(me.getTradeCount());
            result.setProfitLoss(me.getProfitLoss());
            result.setOpenPositionCount(me.getOpenPositionCount());
            result.setSignals(Collections.emptyList());

            match.setResult(result);
            results.add(match);
        }
        return results;
    }

    private List<StockScreenMatch> toLightweightMatches(List<StockScreenMatch> fullMatches) {
        List<StockScreenMatch> light = new ArrayList<>(fullMatches.size());
        for (StockScreenMatch m : fullMatches) {
            StockScreenMatch lm = new StockScreenMatch();
            lm.setTsCode(m.getTsCode());
            lm.setName(m.getName());
            lm.setStrategyId(m.getStrategyId());
            lm.setStrategyName(m.getStrategyName());
            lm.setQuotes(Collections.emptyList());

            BacktestResult r = new BacktestResult();
            BacktestResult src = m.getResult();
            r.setTotalReturn(src.getTotalReturn());
            r.setWinRate(src.getWinRate());
            r.setTradeCount(src.getTradeCount());
            r.setWinningTrades(src.getWinningTrades());
            r.setLosingTrades(src.getLosingTrades());
            r.setMaxDrawdown(src.getMaxDrawdown());
            r.setProfitLoss(src.getProfitLoss());
            r.setOpenPositionCount(src.getOpenPositionCount());
            r.setInitialCapital(src.getInitialCapital());
            r.setFinalCapital(src.getFinalCapital());
            r.setTotalFees(src.getTotalFees());
            r.setSignals(Collections.emptyList());
            lm.setResult(r);

            light.add(lm);
        }
        return light;
    }

    private List<StockScreenMatch> loadLightweightResultsFromDb(String taskId) {
        Optional<ScreenTaskEntity> taskOpt = screenTaskRepository.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            return null;
        }

        List<ScreenMatchRepository.LightweightScreenMatch> projections = screenMatchRepository.findLightweightByTaskId(taskId);
        if (projections.isEmpty()) {
            return new ArrayList<>();
        }

        List<StockScreenMatch> results = new ArrayList<>();
        for (ScreenMatchRepository.LightweightScreenMatch me : projections) {
            StockScreenMatch match = new StockScreenMatch();
            match.setTsCode(me.getTsCode());
            match.setName(me.getName());
            match.setStrategyId(me.getStrategyId());
            match.setStrategyName(me.getStrategyName());
            match.setQuotes(Collections.emptyList());

            BacktestResult result = new BacktestResult();
            result.setTotalReturn(me.getTotalReturn());
            result.setWinRate(me.getWinRate());
            result.setTradeCount(me.getTradeCount());
            result.setProfitLoss(me.getProfitLoss());
            result.setMaxDrawdown(me.getMaxDrawdown());
            result.setOpenPositionCount(me.getOpenPositionCount());
            result.setInitialCapital(me.getInitialCapital());
            result.setFinalCapital(me.getFinalCapital());
            result.setTotalFees(me.getTotalFees());
            result.setSignals(Collections.emptyList());

            match.setResult(result);
            results.add(match);
        }
        return results;
    }

    public boolean cancelTask(String taskId) {
        ScreenTask task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        task.cancelled.set(true);
        return true;
    }

    public void removeTask(String taskId) {
        tasks.remove(taskId);
    }

    public List<ScreenTaskEntity> getHistoryTasks(String username) {
        return screenTaskRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public String getTaskOwner(String taskId) {
        return screenTaskRepository.findByTaskId(taskId).map(ScreenTaskEntity::getUsername).orElse(null);
    }

    @Transactional
    public boolean deleteTask(String taskId) {
        Optional<ScreenTaskEntity> entityOpt = screenTaskRepository.findByTaskId(taskId);
        if (entityOpt.isEmpty()) {
            return false;
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM screen_quote WHERE task_id = ?")) {
            ps.setString(1, taskId);
            int deleted = ps.executeUpdate();
            log.info("批量删除screen_quote: taskId={}, deleted={}", taskId, deleted);
        } catch (Exception e) {
            log.warn("批量删除screen_quote失败: taskId={}, error={}", taskId, e.getMessage());
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM screen_match WHERE task_id = ?")) {
            ps.setString(1, taskId);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("批量删除screen_match失败: taskId={}, error={}", taskId, e.getMessage());
        }

        screenTaskRepository.delete(entityOpt.get());
        tasks.remove(taskId);
        log.info("已删除选股任务: taskId={}", taskId);
        return true;
    }

    private static class ScreenTask {
        String taskId;
        AtomicInteger total;
        AtomicInteger processed;
        AtomicInteger completed;
        AtomicInteger matchCount;
        AtomicBoolean cancelled;
        AtomicReference<String> currentStock;
        AtomicBoolean completedFlag;
        volatile String initError;
        volatile long lastPollTime;
        String startDate;
        String endDate;
        String screenMode;
        final ConcurrentLinkedQueue<StockScreenMatch> matchQueue = new ConcurrentLinkedQueue<>();
        volatile List<StockScreenMatch> cachedResults = null;
    }
}
