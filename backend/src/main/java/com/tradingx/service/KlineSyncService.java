package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingx.config.TushareProperties;
import com.tradingx.model.*;
import com.tradingx.repository.KlineSyncStatusRepository;
import com.tradingx.repository.StockListRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class KlineSyncService {

    private static final Logger log = LoggerFactory.getLogger(KlineSyncService.class);

    private final StockService stockService;
    private final KlineService klineService;
    private final KlineSyncStatusRepository klineSyncStatusRepository;
    private final StockListRepository stockListRepository;
    private final KlineSyncService self;
    private final TushareProperties tushareProperties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KlineSyncService(StockService stockService, KlineService klineService,
                           KlineSyncStatusRepository klineSyncStatusRepository, StockListRepository stockListRepository,
                           @Lazy KlineSyncService self, TushareProperties tushareProperties, RestClient restClient) {
        this.stockService = stockService;
        this.klineService = klineService;
        this.klineSyncStatusRepository = klineSyncStatusRepository;
        this.stockListRepository = stockListRepository;
        this.self = self;
        this.tushareProperties = tushareProperties;
        this.restClient = restClient;
    }

    private static final int CONCURRENCY = 12;
    private static final long TASK_TIMEOUT_MINUTES = 120;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ConcurrentHashMap<String, SyncTask> tasks = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupScheduler;

    private static final Map<String, Integer> DEFAULT_DATA_YEARS = Map.of(
            "day", 20,
            "week", 20,
            "month", 20
    );

    private static final Map<String, String> PERIOD_SPAN_MAP = Map.of(
            "day", "day",
            "week", "week",
            "month", "month"
    );

    @PostConstruct
    public void init() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kline-sync-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(this::cleanupStaleTasks,
                1, 1, TimeUnit.MINUTES);
        log.info("K线同步服务已启动");
    }

    @jakarta.annotation.PreDestroy
    public void destroy() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
    }

    public String startFullSync(KlineSyncRequest request) {
        
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        SyncTask task = new SyncTask();
        task.taskId = taskId;
        task.total = new AtomicInteger(0);
        task.processed = new AtomicInteger(0);
        task.completed = new AtomicInteger(0);
        task.successCount = new AtomicInteger(0);
        task.failCount = new AtomicInteger(0);
        task.cancelled = new AtomicBoolean(false);
        task.completedFlag = new AtomicBoolean(false);
        task.currentStock = new String[1];
        task.currentStock[0] = "加载股票列表...";
        task.currentPeriod = new String[1];
        task.currentPeriod[0] = "";
        task.lastPollTime = System.currentTimeMillis();
        task.request = request;
        task.activeCount = new AtomicInteger(0);
        task.startTime = System.currentTimeMillis();
 

        tasks.put(taskId, task);

        Thread.ofVirtual().start(() -> {
            try {
                List<StockBasic> stocks = stockService.getNonSTStockList();

                if (request.getTsCodes() != null && !request.getTsCodes().isEmpty()) {
                    Set<String> codeSet = new HashSet<>(request.getTsCodes());
                    stocks = stocks.stream().filter(s -> codeSet.contains(s.getTsCode())).toList();
                }

                List<String> periods = request.getPeriods() != null && !request.getPeriods().isEmpty()
                        ? request.getPeriods() : List.of("day", "week", "month");

                task.total.set(stocks.size() * periods.size());
                task.currentStock[0] = "";

                runFullSync(task, stocks, periods, request);
            } catch (Exception e) {
                log.error("K线全量同步初始化失败: taskId={}, error={}", taskId, e.getMessage());
                task.initError = e.getMessage();
                task.completedFlag.set(true);
                task.currentStock[0] = "";
            }
        });

        return taskId;
    }

    private void runFullSync(SyncTask task, List<StockBasic> stocks, List<String> periods, KlineSyncRequest request) {
    // ---- 1. 清理历史数据 ----
    klineSyncStatusRepository.deleteAllBy();
    for (String period : periods) {
        log.info("清空 {} 周期历史数据...", period);
        klineService.truncateKlineTable(period);
        log.info("{} 周期历史数据已清空", period);
    }

    // ---- 2. 构建任务列表 ----
    String endDate = request.getEndDate() != null ? request.getEndDate() : LocalDate.now().format(DATE_FMT);

    Map<String, String> periodStartDates = new HashMap<>();
    for (String period : periods) {
        int dataYears = DEFAULT_DATA_YEARS.getOrDefault(period, 20);
        String startDate = request.getStartDate() != null ? request.getStartDate()
                : LocalDate.now().minusYears(dataYears).format(DATE_FMT);
        periodStartDates.put(period, startDate);
    }

    record SyncJob(String tsCode, String stockName, String period, String startDate) {}

    List<SyncJob> allJobs = new ArrayList<>();
    for (StockBasic stock : stocks) {
        for (String period : periods) {
            allJobs.add(new SyncJob(stock.getTsCode(), stock.getName(), period, periodStartDates.get(period)));
        }
    }

    task.total.set(allJobs.size());
    task.startTime = System.currentTimeMillis();
    task.currentStock[0] = "";
    task.currentPeriod[0] = "";

    // ---- 3. 启动进度打印线程 ----
    Thread progressLogger = Thread.ofVirtual().name("sync-progress").start(() -> {
        while (!task.completedFlag.get()) {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                break;
            }
            if (task.completedFlag.get()) break;

            int completed = task.completed.get();
            int total = task.total.get();
            int active = task.activeCount.get();
            int success = task.successCount.get();
            int fail = task.failCount.get();
            long elapsed = System.currentTimeMillis() - task.startTime;

            double rate = completed > 0 ? (double) completed / elapsed : 0;
            long remainingMs = rate > 0 ? (long) ((total - completed) / rate) : -1;
            String eta = remainingMs > 0 ? formatDuration(remainingMs) : "计算中...";
            double percent = total > 0 ? (double) completed / total * 100 : 0;

            log.info("[同步进度] 活跃:{}/{} | 进度:{}/{} ({}%) | 成功:{} 失败:{} | 耗时:{} 剩余:{}",
                    active, CONCURRENCY, completed, total,
                    String.format("%.1f", percent), success, fail,
                    formatDuration(elapsed), eta);
        }
    });

    // ---- 4. 提交并执行任务 ----
    Semaphore semaphore = new Semaphore(CONCURRENCY);
    List<Future<?>> futures = new ArrayList<>();
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY,
        Thread.ofPlatform().name("kline-sync-", 0).factory());


    for (SyncJob job : allJobs) {
        if (task.cancelled.get()) break;

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            break;
        }

        if (task.cancelled.get()) {
            semaphore.release();
            break;
        }

        futures.add(executor.submit(() -> {
            task.activeCount.incrementAndGet();
            try {
                if (task.cancelled.get()) return;

                task.currentStock[0] = job.stockName() + "(" + job.tsCode() + ")";
                task.currentPeriod[0] = job.period();

                syncSingleStock(job.tsCode(), job.stockName(), job.period(), job.startDate(), endDate, true);
                task.successCount.incrementAndGet();
            } catch (Exception e) {
                log.warn("同步失败: tsCode={}, period={}, error={}", job.tsCode(), job.period(), e.getMessage());
                task.failCount.incrementAndGet();
            } finally {
                task.activeCount.decrementAndGet();
                task.completed.incrementAndGet();
                task.lastPollTime = System.currentTimeMillis();
                semaphore.release();
            }
        }));
    }

    // ---- 5. 等待所有任务完成 ----
    for (Future<?> future : futures) {
        try {
            future.get();
        } catch (Exception e) {
            log.warn("任务执行异常: {}", e.getMessage());
        }
    }

    executor.shutdown();

    // ---- 6. 标记完成，进度线程退出 ----
    task.completedFlag.set(true);
    progressLogger.interrupt();

    task.currentStock[0] = "";
    task.currentPeriod[0] = "";

    long totalElapsed = System.currentTimeMillis() - task.startTime;
    log.info("K线同步完成: taskId={}, 成功={}, 失败={}, 已处理={}/{}, 总耗时={}",
            task.taskId, task.successCount.get(), task.failCount.get(),
            task.completed.get(), task.total.get(), formatDuration(totalElapsed));
}

private String formatDuration(long ms) {
    long seconds = ms / 1000;
    if (seconds < 60) return seconds + "秒";
    long minutes = seconds / 60;
    seconds = seconds % 60;
    if (minutes < 60) return minutes + "分" + seconds + "秒";
    long hours = minutes / 60;
    minutes = minutes % 60;
    return hours + "时" + minutes + "分";
}


    public void syncSingleStock(String tsCode, String stockName, String period, String startDate, String endDate) {
        syncSingleStock(tsCode, stockName, period, startDate, endDate, false);
    }

    public void syncSingleStock(String tsCode, String stockName, String period, String startDate, String endDate, boolean skipDelete) {
        long startMs = System.currentTimeMillis();
        try {
            if (!skipDelete) {
                klineService.deleteKlineDataByTsCode(tsCode, period);
            }

            DailyQuery query = new DailyQuery();
            query.setTsCode(tsCode);
            query.setStartDate(startDate);
            query.setEndDate(endDate);
            query.setPeriod(PERIOD_SPAN_MAP.getOrDefault(period, "day"));
            query.setForceRefresh(true);

            List<DailyQuote> quotes = stockService.queryDaily(query);

            if (quotes == null || quotes.isEmpty()) {
                self.updateSyncStatus(tsCode, stockName, period, null, "EMPTY", 0, "API返回空数据");
                return;
            }

            klineService.saveKlineData(tsCode, period, quotes);

            String firstDate = quotes.get(0).getTradeDate();
            String lastDate = quotes.get(quotes.size() - 1).getTradeDate();
            self.updateSyncStatus(tsCode, stockName, period, lastDate, "SUCCESS", quotes.size(), null, firstDate);

            long elapsed = System.currentTimeMillis() - startMs;
            // if (elapsed > 3000) {
            //     log.warn("同步慢查询: tsCode={}, period={}, count={}, elapsed={}ms", tsCode, period, quotes.size(), elapsed);
            // }
        } catch (Exception e) {
            log.error("同步失败: tsCode={}, period={}, error={}", tsCode, period, e.getMessage());
            try {
                self.updateSyncStatus(tsCode, stockName, period, null, "FAILED", 0, e.getMessage());
            } catch (Exception ex) {
                log.error("更新同步状态失败: tsCode={}, period={}", tsCode, period, ex);
            }
        }
    }

    @Transactional
    public void updateSyncStatus(String tsCode, String stockName, String period, String lastSyncDate, String status, int totalRecords, String errorMessage) {
        updateSyncStatus(tsCode, stockName, period, lastSyncDate, status, totalRecords, errorMessage, null);
    }

    @Transactional
    public void updateSyncStatus(String tsCode, String stockName, String period, String lastSyncDate, String status, int totalRecords, String errorMessage, String firstDate) {
        try {
            Optional<KlineSyncStatusEntity> existing = klineSyncStatusRepository.findByTsCodeAndPeriod(tsCode, period);

            KlineSyncStatusEntity entity;
            if (existing.isPresent()) {
                entity = existing.get();
            } else {
                entity = new KlineSyncStatusEntity();
                entity.setTsCode(tsCode);
                entity.setPeriod(period);
                entity.setDataYears(DEFAULT_DATA_YEARS.getOrDefault(period, 6));
            }

            if (stockName != null) entity.setStockName(stockName);
            if (lastSyncDate != null) entity.setLastSyncDate(lastSyncDate);
            entity.setStatus(status);
            entity.setTotalRecords(totalRecords);
            
            if ("SUCCESS".equals(status)) {
                entity.setConsecutiveFailures(0);
                entity.setErrorMessage(null);
                if (firstDate != null) {
                    entity.setStartDate(firstDate);
                } else {
                    String dbFirstDate = klineService.getFirstSyncDate(tsCode, period);
                    entity.setStartDate(dbFirstDate);
                }
            } else {
                entity.setConsecutiveFailures(entity.getConsecutiveFailures() != null ? entity.getConsecutiveFailures() + 1 : 1);
                entity.setErrorMessage(errorMessage);
            }

            klineSyncStatusRepository.save(entity);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("同步状态并发写入冲突，重新查询更新: tsCode={}, period={}", tsCode, period);
            Optional<KlineSyncStatusEntity> existing = klineSyncStatusRepository.findByTsCodeAndPeriod(tsCode, period);
            if (existing.isPresent()) {
                KlineSyncStatusEntity entity = existing.get();
                if (stockName != null) entity.setStockName(stockName);
                if (lastSyncDate != null) entity.setLastSyncDate(lastSyncDate);
                entity.setStatus(status);
                entity.setTotalRecords(totalRecords);
                if ("SUCCESS".equals(status)) {
                    entity.setConsecutiveFailures(0);
                    entity.setErrorMessage(null);
                } else {
                    entity.setConsecutiveFailures(entity.getConsecutiveFailures() != null ? entity.getConsecutiveFailures() + 1 : 1);
                    entity.setErrorMessage(errorMessage);
                }
                klineSyncStatusRepository.save(entity);
            }
        }
    }

    @Scheduled(cron = "0 36 15 * * ?")
    public void scheduledFullSync() {
        java.time.DayOfWeek dow = LocalDate.now().getDayOfWeek();
        if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
            log.info("定时全量同步: 跳过周末({})", dow);
            return;
        }

        try {
            Boolean isTradeDay = isTradeDay(LocalDate.now());
            if (isTradeDay == null) {
                log.error("定时全量同步: 交易日历查询失败，跳过本次同步");
                return;
            }
            if (!isTradeDay) {
                log.info("定时全量同步: 今日非交易日(节假日)，跳过");
                return;
            }
        } catch (Exception e) {
            log.error("定时全量同步: 交易日历查询异常，跳过本次同步", e);
            return;
        }

        log.info("定时全量同步: 开始");
        try {
            log.info("定时全量同步: 1. 刷新股票列表");
            stockService.refreshStockList();
            log.info("定时全量同步: 股票列表刷新完成");
            
            log.info("定时全量同步: 2. 同步K线数据");
            KlineSyncRequest request = new KlineSyncRequest();
            request.setPeriods(List.of("day", "week", "month"));
            startFullSync(request);
            log.info("定时全量同步: 已启动");
        } catch (Exception e) {
            log.error("定时全量同步异常", e);
        }
    }

    public Boolean isTradeDay(LocalDate date) {
        String token = tushareProperties.getToken();
        if (token == null || token.isBlank()) {
            log.warn("tushare token 未配置，无法判断交易日");
            return null;
        }

        try {
            String dateStr = date.format(DATE_FMT);
            String requestBody = String.format(
                    "{\"api_name\":\"trade_cal\",\"token\":\"%s\",\"params\":{\"exchange\":\"SSE\",\"start_date\":\"%s\",\"end_date\":\"%s\"},\"fields\":\"cal_date,is_open\"}",
                    token, dateStr, dateStr);

            String response = restClient.post()
                    .uri(tushareProperties.getApiUrl())
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if (response == null) {
                log.warn("tushare trade_cal 返回空响应");
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                log.warn("tushare trade_cal 返回错误: code={}, msg={}", code, root.path("msg").asText());
                return null;
            }

            JsonNode items = root.path("data").path("items");
            if (!items.isArray() || items.isEmpty()) {
                log.warn("tushare trade_cal 无数据: date={}", dateStr);
                return null;
            }

            int isOpen = items.get(0).get(1).asInt(-1);
            return isOpen == 1;
        } catch (Exception e) {
            log.error("tushare trade_cal 调用失败", e);
            return null;
        }
    }

    public KlineSyncProgress getProgress(String taskId) {
        SyncTask task = tasks.get(taskId);
        if (task == null) return null;

        task.lastPollTime = System.currentTimeMillis();

        KlineSyncProgress progress = new KlineSyncProgress();
        progress.setTaskId(task.taskId);
        progress.setCurrent(task.completed.get());
        progress.setTotal(task.total.get());
        progress.setCurrentStock(task.currentStock[0] != null ? task.currentStock[0] : "");
        progress.setCurrentPeriod(task.currentPeriod[0] != null ? task.currentPeriod[0] : "");
        progress.setCompleted(task.completedFlag.get());
        progress.setCancelled(task.cancelled.get());
        progress.setSuccessCount(task.successCount.get());
        progress.setFailCount(task.failCount.get());
        if (task.initError != null) progress.setInitError(task.initError);
        return progress;
    }

    public boolean cancelTask(String taskId) {
        SyncTask task = tasks.get(taskId);
        if (task == null) return false;
        task.cancelled.set(true);
        return true;
    }

    public KlineDashboard getDashboard() {
        List<KlineSyncStatusEntity> dayStatus = klineSyncStatusRepository.findByPeriodOrderByTsCode("day");
        long dayComplete = klineSyncStatusRepository.countByPeriodAndStatus("day", "SUCCESS");

        MissingDataResult missingData = new MissingDataResult();
        missingData.setTotalStocks(dayStatus.size());
        missingData.setCompleteStocks((int) dayComplete);
        missingData.setIncompleteStocks(missingData.getTotalStocks() - missingData.getCompleteStocks());
        missingData.setAvgCompletionRate(missingData.getTotalStocks() > 0
                ? (double) missingData.getCompleteStocks() / missingData.getTotalStocks() * 100 : 0);
        List<MissingDataResult.MissingDetail> details = klineSyncStatusRepository.findByStatusNotSuccess().stream()
                .map(s -> {
                    MissingDataResult.MissingDetail d = new MissingDataResult.MissingDetail();
                    d.setTsCode(s.getTsCode());
                    d.setStockName(s.getStockName());
                    d.setPeriod(s.getPeriod());
                    d.setMissingDays(s.getConsecutiveFailures() != null ? s.getConsecutiveFailures() : 0);
                    d.setFirstMissingDate(s.getLastSyncDate());
                    d.setLastMissingDate(s.getLastSyncDate());
                    return d;
                })
                .toList();
        missingData.setMissingDetails(details);

        Map<String, KlineRangeInfo> rangeMap = new LinkedHashMap<>();
        for (String p : List.of("day", "week", "month")) {
            String minDate = klineService.getGlobalStartDate(p);
            String maxDate = klineService.getGlobalEndDate(p);
            long stockCount = klineSyncStatusRepository.countSuccessByPeriod(p);
            long totalRecords = klineService.countRecords(p);

            KlineRangeInfo info = new KlineRangeInfo();
            info.setPeriod(p);
            info.setStartDate(minDate);
            info.setEndDate(maxDate);
            info.setStockCount((int) stockCount);
            info.setTotalRecords(totalRecords);
            rangeMap.put(p, info);
        }

        KlineDashboard dashboard = new KlineDashboard();
        dashboard.setMissingData(missingData);
        dashboard.setRangeMap(rangeMap);
        return dashboard;
    }

    public List<KlineSyncStatusEntity> getSyncStatus(String period) {
        if (period != null && !period.isBlank()) {
            return klineSyncStatusRepository.findByPeriodOrderByTsCode(period);
        }
        return klineSyncStatusRepository.findAll();
    }

    public Page<KlineSyncStatusEntity> getSyncStatusPaged(String period, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (period != null && !period.isBlank()) {
            return klineSyncStatusRepository.findByPeriodOrderByTsCode(period, pageable);
        }
        return klineSyncStatusRepository.findAllByOrderByTsCode(pageable);
    }

    public Map<String, KlineRangeInfo> getGlobalDateRange() {
        Map<String, KlineRangeInfo> map = new LinkedHashMap<>();
        
        for (String period : List.of("day", "week", "month")) {
            String minDate = klineService.getGlobalStartDate(period);
            String maxDate = klineService.getGlobalEndDate(period);
            long stockCount = klineSyncStatusRepository.countSuccessByPeriod(period);
            long totalRecords = klineService.countRecords(period);
            
            KlineRangeInfo info = new KlineRangeInfo();
            info.setPeriod(period);
            info.setStartDate(minDate);
            info.setEndDate(maxDate);
            info.setStockCount((int) stockCount);
            info.setTotalRecords(totalRecords);
            map.put(period, info);
        }
        return map;
    }

    public MissingDataResult getMissingDataStats() {
        List<KlineSyncStatusEntity> dayStatus = klineSyncStatusRepository.findByPeriodOrderByTsCode("day");
        long dayComplete = klineSyncStatusRepository.countByPeriodAndStatus("day", "SUCCESS");

        MissingDataResult result = new MissingDataResult();
        result.setTotalStocks(dayStatus.size());
        result.setCompleteStocks((int) dayComplete);
        result.setIncompleteStocks(result.getTotalStocks() - result.getCompleteStocks());
        result.setAvgCompletionRate(result.getTotalStocks() > 0
                ? (double) result.getCompleteStocks() / result.getTotalStocks() * 100 : 0);

        List<MissingDataResult.MissingDetail> details = klineSyncStatusRepository.findByStatusNotSuccess().stream()
                .map(s -> {
                    MissingDataResult.MissingDetail d = new MissingDataResult.MissingDetail();
                    d.setTsCode(s.getTsCode());
                    d.setStockName(s.getStockName());
                    d.setPeriod(s.getPeriod());
                    d.setMissingDays(s.getConsecutiveFailures() != null ? s.getConsecutiveFailures() : 0);
                    d.setFirstMissingDate(s.getLastSyncDate());
                    d.setLastMissingDate(s.getLastSyncDate());
                    return d;
                })
                .toList();

        result.setMissingDetails(details);
        return result;
    }

    public String fillMissingData() {
        List<KlineSyncStatusEntity> incomplete = klineSyncStatusRepository.findByStatusNotSuccess();

        if (incomplete.isEmpty()) return null;

        KlineSyncRequest request = new KlineSyncRequest();
        request.setPeriods(incomplete.stream().map(KlineSyncStatusEntity::getPeriod).distinct().toList());
        request.setTsCodes(incomplete.stream().map(KlineSyncStatusEntity::getTsCode).distinct().toList());
        request.setStartDate(LocalDate.now().minusYears(6).format(DATE_FMT));
        request.setEndDate(LocalDate.now().format(DATE_FMT));

        return startFullSync(request);
    }

    private void cleanupStaleTasks() {
        try {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, SyncTask> entry : tasks.entrySet()) {
                SyncTask task = entry.getValue();
                if (task.completedFlag.get()) continue;
                long elapsed = now - task.lastPollTime;
                if (elapsed > TASK_TIMEOUT_MINUTES * 60 * 1000) {
                    log.warn("K线同步任务超时取消: taskId={}, 已处理={}/{}", task.taskId, task.completed.get(), task.total.get());
                    task.cancelled.set(true);
                    task.completedFlag.set(true);
                }
            }

            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, SyncTask> entry : tasks.entrySet()) {
                SyncTask task = entry.getValue();
                if (task.completedFlag.get() && (now - task.lastPollTime > 10 * 60 * 1000)) {
                    toRemove.add(entry.getKey());
                }
            }
            for (String taskId : toRemove) {
                tasks.remove(taskId);
            }
        } catch (Exception e) {
            log.error("清理同步任务异常", e);
        }
    }

    private static class SyncTask {
        String taskId;
        AtomicInteger total;
        AtomicInteger processed;
        AtomicInteger completed;
        AtomicInteger successCount;
        AtomicInteger failCount;
        AtomicBoolean cancelled;
        String[] currentStock;
        String[] currentPeriod;
        AtomicBoolean completedFlag;
        volatile String initError;
        volatile long lastPollTime;
        KlineSyncRequest request;
        volatile long startTime;
        AtomicInteger activeCount = new AtomicInteger(0);   // ← 新增：当前正在干活的线程数
    }
}
