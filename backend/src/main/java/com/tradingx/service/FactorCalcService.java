package com.tradingx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingx.model.*;
import com.tradingx.repository.FactorEvalTaskRepository;
import com.tradingx.repository.StockListRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.MACDVIndicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.WilliamsRIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.num.DecimalNumFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.DoubleStream;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class FactorCalcService {

    private static final Logger log = LoggerFactory.getLogger(FactorCalcService.class);

    // ========================= 依赖 =========================

    private final KlineService klineService;
    private final StockListRepository stockListRepository;
    private final FactorEvalTaskRepository factorEvalTaskRepository;
    private final FactorEvalService factorEvalService;
    private final FactorCombinationService factorCombinationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FactorCalcService(KlineService klineService,
                             StockListRepository stockListRepository,
                             FactorEvalTaskRepository factorEvalTaskRepository,
                             @Lazy FactorEvalService factorEvalService,
                             @Lazy FactorCombinationService factorCombinationService) {
        this.klineService = klineService;
        this.stockListRepository = stockListRepository;
        this.factorEvalTaskRepository = factorEvalTaskRepository;
        this.factorEvalService = factorEvalService;
        this.factorCombinationService = factorCombinationService;
    }

    // ========================= 常量 =========================

    /** A 股市场时区 */
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    /** 日期格式 */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 并发度 */
    private static final int CONCURRENCY = 15;

    /** 任务超时（分钟） */
    private static final long TASK_TIMEOUT_MINUTES = 5;

    /** 清理检查间隔（分钟） */
    private static final long CLEANUP_INTERVAL_MINUTES = 1;

    /**
     * 各技术指标所需的最大预热 bar 数。
     * <pre>
     * EMA(26)          ≈ 78 bar（3 倍周期收敛）
     * RSI(14) / ATR(14)≈ 42 bar（Wilder 平滑 3 倍周期）
     * SMA(30)          = 29 bar
     * StdDev/Boll(20)  = 19 bar
     * Highest/Low(20)  = 19 bar
     * 偏度/峰度(20)     = 19 bar
     * </pre>
     * 取最大值 78 + 安全余量 → 100
     */
    private static final int MIN_WARMUP_BARS = 100;

    /**
     * 日历天 → 交易日的近似转换系数（保守值）。
     * A 股年均 ~245 交易日 / 365 日历天 ≈ 0.67，倒数 ≈ 1.5，取 2.0 以覆盖长假。
     */
    private static final double CALENDAR_TO_TRADING_RATIO = 2.0;

    /** 向后预留的额外 bar 数 */
    private static final int FUTURE_BUFFER_BARS = 30;

    // ========================= 因子定义 =========================

    private static final List<FactorDef> FACTOR_DEFINITIONS = List.of(
            new FactorDef("return_rate",    "收益率",       "price",      false),
            new FactorDef("amplitude",      "振幅",         "price",      false),
            new FactorDef("gap",            "跳空缺口",     "price",      false),
            new FactorDef("high_low_dist",  "新高新低距离", "price",      false),
            new FactorDef("sma5_sma20",     "SMA5/SMA20",   "ma",         true),
            new FactorDef("sma10_sma30",    "SMA10/SMA30",  "ma",         false),
            new FactorDef("ema12_ema26",    "EMA12/EMA26",  "ma",         true),
            new FactorDef("rsi_14",         "RSI(14)",      "oscillator", true),
            new FactorDef("macd_hist",      "MACD柱",       "oscillator", true),
            new FactorDef("kdj_k",          "KDJ-K",        "oscillator", true),
            new FactorDef("cci_14",         "CCI(14)",      "oscillator", false),
            new FactorDef("wr_14",          "WR(14)",       "oscillator", false),
            new FactorDef("roc_10",         "ROC(10)",      "oscillator", true),
            new FactorDef("atr_14",         "ATR(14)",      "volatility", true),
            new FactorDef("boll_position",  "布林带位置",   "volatility", true),
            new FactorDef("std_20",         "标准差(20)",   "volatility", false),
            new FactorDef("turnover",       "换手率",       "volume",     true),
            new FactorDef("volume_ratio",   "量比",         "volume",     true),
            new FactorDef("obv",            "OBV",          "volume",     false),
            new FactorDef("skewness_20",    "偏度(20)",     "stats",      false),
            new FactorDef("kurtosis_20",    "峰度(20)",     "stats",      false)
    );

    // ========================= 任务管理 =========================

    private final ConcurrentHashMap<String, EvalTask> tasks = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupScheduler;
    /** 外层任务执行器，便于优雅关闭 */
    private ExecutorService taskLauncher;

    @PostConstruct
    public void init() {
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "factor-calc-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(this::cleanupStaleTasks,
                CLEANUP_INTERVAL_MINUTES, CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);

        taskLauncher = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "factor-task-launcher");
            t.setDaemon(true);
            return t;
        });

        log.info("因子计算服务已启动: 超时={}分钟, 清理间隔={}分钟, 并发度={}",
                TASK_TIMEOUT_MINUTES, CLEANUP_INTERVAL_MINUTES, CONCURRENCY);
    }

    @PreDestroy
    public void destroy() {
        // 1. 取消所有运行中任务
        for (EvalTask task : tasks.values()) {
            task.cancelled.set(true);
        }
        // 2. 关闭清理调度器
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdownNow();
        }
        // 3. 关闭任务启动器
        if (taskLauncher != null) {
            taskLauncher.shutdownNow();
        }
        log.info("因子计算服务已关闭");
    }

    private void cleanupStaleTasks() {
        try {
            long now = System.currentTimeMillis();
            long timeoutMs = TASK_TIMEOUT_MINUTES * 60_000;
            long doneRetainMs = 2 * 60_000;

            List<String> toRemove = new ArrayList<>();

            for (Map.Entry<String, EvalTask> entry : tasks.entrySet()) {
                EvalTask task = entry.getValue();

                // 未完成 + 超时 → 自动取消
                if (!task.completedFlag.get()) {
                    long elapsed = now - task.lastPollTime;
                    if (elapsed > timeoutMs) {
                        log.warn("任务超时自动取消: taskId={}, 已{}分钟无轮询",
                                task.taskId, elapsed / 60_000);
                        task.cancelled.set(true);
                        task.completedFlag.set(true);
                        factorEvalTaskRepository.findByTaskId(task.taskId).ifPresent(entity -> {
                            entity.setCompleted(true);
                            factorEvalTaskRepository.save(entity);
                        });
                    }
                }

                // 已完成 + 超过保留时间 → 移除
                if (task.completedFlag.get() && (now - task.lastPollTime > doneRetainMs)) {
                    toRemove.add(entry.getKey());
                }
            }

            for (String id : toRemove) {
                tasks.remove(id);
                log.info("清理已完成任务: taskId={}", id);
            }
        } catch (Exception e) {
            log.error("清理任务异常", e);
        }
    }

    // ========================= 因子计算（单股票） =========================

    /**
     * 计算单只股票在指定日期范围内的全部因子值。
     *
     * @param tsCode      股票代码
     * @param startDate   目标起始日期（yyyyMMdd），null 表示不限
     * @param endDate     目标结束日期（yyyyMMdd），null 表示不限
     * @param forwardDays 前瞻天数（用于预留尾部数据）
     * @return 日期 → 因子名 → 因子值
     */
    public Map<String, Map<String, Double>> calcFactorValues(
            String tsCode, String startDate, String endDate, int forwardDays) {

        // ===== 1. 计算数据查询范围 =====
        int futureBars = Math.max(forwardDays + FUTURE_BUFFER_BARS, 60);
        int warmupCalendarDays = (int) Math.ceil(MIN_WARMUP_BARS * CALENDAR_TO_TRADING_RATIO * 1.5);

        String queryStart = startDate;
        String queryEnd = endDate;

        if (isNotBlank(startDate)) {
            try {
                LocalDate sd = LocalDate.parse(startDate, DATE_FMT);
                queryStart = sd.minusDays(warmupCalendarDays).format(DATE_FMT);
            } catch (Exception ignored) { }
        }
        if (isNotBlank(endDate)) {
            try {
                LocalDate ed = LocalDate.parse(endDate, DATE_FMT);
                queryEnd = ed.plusDays((long) futureBars * 2).format(DATE_FMT);
            } catch (Exception ignored) { }
        }

        // ===== 2. 获取并排序 K 线数据 =====
        List<DailyQuote> allQuotes;
        if (isNotBlank(queryStart) && isNotBlank(queryEnd)) {
            allQuotes = klineService.getKlineData(tsCode, "day", queryStart, queryEnd);
        } else {
            allQuotes = klineService.getKlineData(tsCode, "day");
        }

        if (allQuotes == null || allQuotes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DailyQuote> sorted = new ArrayList<>(allQuotes);
        sorted.sort(Comparator.comparing(DailyQuote::getTradeDate));
        int dataSize = sorted.size();

        // ===== 3. 定位目标日期范围在数据中的索引 =====
        int startIdx = 0;
        int endIdx = dataSize - 1;

        if (isNotBlank(startDate)) {
            for (int i = 0; i < dataSize; i++) {
                if (sorted.get(i).getTradeDate().compareTo(startDate) >= 0) {
                    startIdx = i;
                    break;
                }
            }
        }
        if (isNotBlank(endDate)) {
            for (int i = dataSize - 1; i >= 0; i--) {
                if (sorted.get(i).getTradeDate().compareTo(endDate) <= 0) {
                    endIdx = i;
                    break;
                }
            }
        }

        if (startIdx > endIdx) {
            return Collections.emptyMap();
        }

        // ===== 4. 确定计算范围并验证预热充足性 =====
        int calcStartIdx = Math.max(0, startIdx - MIN_WARMUP_BARS);
        int actualWarmupBars = startIdx - calcStartIdx;
        boolean warmupSufficient = actualWarmupBars >= MIN_WARMUP_BARS;

        if (!warmupSufficient) {
            log.debug("股票 {} 预热期不足: 需要 {} bar, 实际 {} bar (可能是次新股)",
                    tsCode, MIN_WARMUP_BARS, actualWarmupBars);
        }

        int calcEndIdx = Math.min(dataSize - 1, endIdx + futureBars);
        List<DailyQuote> calcQuotes = sorted.subList(calcStartIdx, calcEndIdx + 1);

        if (calcQuotes.isEmpty()) {
            return Collections.emptyMap();
        }

        // ===== 5. 构建 BarSeries =====
        BarSeries series = convertToBarSeries(calcQuotes);
        int barCount = series.getBarCount();
        if (barCount == 0) {
            return Collections.emptyMap();
        }

        // Series 中 startIdx 对应的 bar 索引
        int outputStartInSeries = startIdx - calcStartIdx;

        // 如果预热不足，后移输出起始点以保证指标准确
        int effectiveStartBar;
        if (warmupSufficient) {
            effectiveStartBar = outputStartInSeries;
        } else {
            // 尽量从 MIN_WARMUP_BARS 开始，但不超过 bar 总数
            effectiveStartBar = Math.min(MIN_WARMUP_BARS, barCount - 1);

            // 如果调整后的起始日期已超出 endDate 范围，跳过
            String adjustedStart = extractDateFromBar(series, effectiveStartBar);
            if (isNotBlank(endDate) && adjustedStart.compareTo(endDate) > 0) {
                log.warn("股票 {} 预热修正后无有效输出日期，跳过", tsCode);
                return Collections.emptyMap();
            }

            log.debug("股票 {} 输出起始日调整: {} → {}（预热不足修正）",
                    tsCode, startDate, adjustedStart);
        }

        // 确保不超出 Series 范围
        if (effectiveStartBar >= barCount) {
            log.warn("股票 {} 数据量过少，无法满足最小预热需求，跳过", tsCode);
            return Collections.emptyMap();
        }

        // ===== 6. 构建指标 =====
        BigDecimal circulatingShares = stockListRepository
                .findByStockCode(tsCode)
                .map(StockListEntity::getCirculatingShares)
                .orElse(null);

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        HighPriceIndicator highPrice = new HighPriceIndicator(series);
        LowPriceIndicator lowPrice = new LowPriceIndicator(series);
        OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        SMAIndicator sma5  = new SMAIndicator(closePrice, 5);
        SMAIndicator sma10 = new SMAIndicator(closePrice, 10);
        SMAIndicator sma20 = new SMAIndicator(closePrice, 20);
        SMAIndicator sma30 = new SMAIndicator(closePrice, 30);
        EMAIndicator ema12 = new EMAIndicator(closePrice, 12);
        EMAIndicator ema26 = new EMAIndicator(closePrice, 26);

        RSIIndicator rsi14 = new RSIIndicator(closePrice, 14);
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        MACDVIndicator macdHist = new MACDVIndicator(macd);
        StochasticOscillatorKIndicator stochK = new StochasticOscillatorKIndicator(series, 9);
        CCIIndicator cci14 = new CCIIndicator(series, 14);
        WilliamsRIndicator wr14 = new WilliamsRIndicator(series, 14);
        ROCIndicator roc10 = new ROCIndicator(closePrice, 10);

        org.ta4j.core.indicators.ATRIndicator atr14 =
                new org.ta4j.core.indicators.ATRIndicator(series, 14);

        BollingerBandsMiddleIndicator bollMiddle = new BollingerBandsMiddleIndicator(sma20);
        StandardDeviationIndicator std20 = new StandardDeviationIndicator(closePrice, 20);
        BollingerBandsUpperIndicator bollUpper = new BollingerBandsUpperIndicator(bollMiddle, std20);
        BollingerBandsLowerIndicator bollLower = new BollingerBandsLowerIndicator(bollMiddle, std20);

        SMAIndicator volSma5 = new SMAIndicator(volume, 5);
        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(series);

        HighestValueIndicator highest20 = new HighestValueIndicator(highPrice, 20);
        LowestValueIndicator lowest20 = new LowestValueIndicator(lowPrice, 20);

        // ===== 7. 逐 bar 提取因子值 =====
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();

        for (int i = effectiveStartBar; i < barCount; i++) {
            String tradeDate = extractDateFromBar(series, i);

            // 超出 endDate 则停止
            if (isNotBlank(endDate) && tradeDate.compareTo(endDate) > 0) {
                break;
            }

            double close = closePrice.getValue(i).doubleValue();
            double open = openPrice.getValue(i).doubleValue();
            double high = highPrice.getValue(i).doubleValue();
            double low = lowPrice.getValue(i).doubleValue();
            double vol = volume.getValue(i).doubleValue();
            double prevClose = (i > 0) ? closePrice.getValue(i - 1).doubleValue() : close;

            Map<String, Double> fv = new LinkedHashMap<>(24);

            // --- price ---
            fv.put("return_rate", (i > 0 && prevClose != 0)
                    ? (close - prevClose) / prevClose
                    : 0.0);

            fv.put("pct_chg", (i > 0 && prevClose != 0)
                    ? (close - prevClose) / prevClose * 100.0
                    : 0.0);

            fv.put("amplitude", (i > 0 && prevClose != 0)
                    ? (high - low) / prevClose
                    : 0.0);

            fv.put("gap", (i > 0 && prevClose != 0)
                    ? (open - prevClose) / prevClose
                    : 0.0);

            double highestVal = highest20.getValue(i).doubleValue();
            double lowestVal = lowest20.getValue(i).doubleValue();
            fv.put("high_low_dist", safeDiv(highestVal - lowestVal, highestVal, 0.0));

            // --- ma ---
            double sma5Val  = sma5.getValue(i).doubleValue();
            double sma20Val = sma20.getValue(i).doubleValue();
            fv.put("sma5_sma20", safeDiv(sma5Val, sma20Val, 1.0));

            double sma10Val = sma10.getValue(i).doubleValue();
            double sma30Val = sma30.getValue(i).doubleValue();
            fv.put("sma10_sma30", safeDiv(sma10Val, sma30Val, 1.0));

            double ema12Val = ema12.getValue(i).doubleValue();
            double ema26Val = ema26.getValue(i).doubleValue();
            fv.put("ema12_ema26", safeDiv(ema12Val, ema26Val, 1.0));

            // --- oscillator ---
            fv.put("rsi_14",    toNull(rsi14.getValue(i).doubleValue()));
            fv.put("macd_hist", toNull(macdHist.getValue(i).doubleValue()));
            fv.put("kdj_k",     toNull(stochK.getValue(i).doubleValue()));
            fv.put("cci_14",    toNull(cci14.getValue(i).doubleValue()));
            fv.put("wr_14",     toNull(wr14.getValue(i).doubleValue()));
            fv.put("roc_10",    toNull(roc10.getValue(i).doubleValue()));

            // --- volatility ---
            fv.put("atr_14", toNull(atr14.getValue(i).doubleValue()));

            double bollUpperVal = bollUpper.getValue(i).doubleValue();
            double bollLowerVal = bollLower.getValue(i).doubleValue();
            double bollWidth = bollUpperVal - bollLowerVal;
            fv.put("boll_position", safeDiv(close - bollLowerVal, bollWidth, 0.5));

            fv.put("std_20", toNull(std20.getValue(i).doubleValue()));

            // --- volume ---
            if (circulatingShares != null && circulatingShares.signum() > 0) {
                double circ = circulatingShares.doubleValue();
                fv.put("turnover", circ != 0 ? toNull(vol / circ * 100.0) : null);
            } else {
                fv.put("turnover", null);
            }

            double volSma5Val = volSma5.getValue(i).doubleValue();
            fv.put("volume_ratio", safeDiv(vol, volSma5Val, 1.0));

            fv.put("obv", toNull(obv.getValue(i).doubleValue()));

            // --- stats (需要 20 日窗口) ---
            if (i >= 19) {
                double[] returns = new double[20];
                for (int j = 0; j < 20; j++) {
                    int idx = i - 19 + j;
                    double prev = (idx > 0)
                            ? closePrice.getValue(idx - 1).doubleValue()
                            : closePrice.getValue(idx).doubleValue();
                    returns[j] = (prev != 0)
                            ? (closePrice.getValue(idx).doubleValue() - prev) / prev
                            : 0.0;
                }
                fv.put("skewness_20", calcSkewness(returns));
                fv.put("kurtosis_20", calcKurtosis(returns));
            } else {
                fv.put("skewness_20", null);
                fv.put("kurtosis_20", null);
            }

            result.put(tradeDate, fv);
        }

        return result;
    }

    // 兼容无 forwardDays 的调用
    public Map<String, Map<String, Double>> calcFactorValues(
            String tsCode, String startDate, String endDate) {
        return calcFactorValues(tsCode, startDate, endDate, 60);
    }

    // ========================= 任务启动 =========================

    public String startCalcAndEval(FactorEvalRequest request) {
        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        EvalTask task = new EvalTask();
        task.taskId = taskId;
        task.total = new AtomicInteger(0);
        task.completed = new AtomicInteger(0);
        task.cancelled = new AtomicBoolean(false);
        task.currentStock = new AtomicReference<>("加载股票列表...");
        task.currentFactor = new AtomicReference<>("");
        task.completedFlag = new AtomicBoolean(false);
        task.initError = null;
        task.lastPollTime = System.currentTimeMillis();
        task.request = request;
        task.factorData = new ConcurrentHashMap<>();
        task.factorCompleted = new AtomicInteger(0);
        task.totalFactors = new AtomicInteger(0);

        tasks.put(taskId, task);

        taskLauncher.submit(() -> {
            try {
                List<StockListEntity> stocks = stockListRepository.findNonSTStocks();
                task.total.set(stocks.size());
                task.currentStock.set("");

                // 创建 DB 记录
                FactorEvalTaskEntity taskEntity = new FactorEvalTaskEntity();
                taskEntity.setTaskId(taskId);
                taskEntity.setUsername(request.getUsername());
                taskEntity.setStartDate(request.getStartDate());
                taskEntity.setEndDate(request.getEndDate());
                taskEntity.setFactorNames(request.getFactorNames() != null
                        ? String.join(",", request.getFactorNames()) : "");
                taskEntity.setForwardDays(request.getForwardDays());
                taskEntity.setTotalStocks(stocks.size());
                taskEntity.setCompleted(false);
                factorEvalTaskRepository.save(taskEntity);

                runCalcTask(task, stocks);
            } catch (Exception e) {
                log.error("任务初始化失败: taskId={}, error={}", taskId, e.getMessage(), e);
                task.initError = e.getMessage();
                task.completedFlag.set(true);
                task.currentStock.set("");
                factorEvalTaskRepository.findByTaskId(taskId).ifPresent(entity -> {
                    entity.setCompleted(true);
                    entity.setInitError(e.getMessage());
                    factorEvalTaskRepository.save(entity);
                });
            }
        });

        return taskId;
    }

    // ========================= 任务执行 =========================

    private void runCalcTask(EvalTask task, List<StockListEntity> stocks) {
        // 阶段 1: 并发计算各股票因子
        Semaphore semaphore = new Semaphore(CONCURRENCY);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();

        for (StockListEntity stock : stocks) {
            if (task.cancelled.get()) break;

            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (task.cancelled.get()) {
                semaphore.release();
                break;
            }

            task.currentStock.set(stock.getStockName() + "(" + stock.getStockCode() + ")");

            futures.add(executor.submit(() -> {
                try {
                    if (task.cancelled.get()) return;

                    Map<String, Map<String, Double>> factorValues = calcFactorValues(
                            stock.getStockCode(),
                            task.request.getStartDate(),
                            task.request.getEndDate(),
                            task.request.getForwardDays());

                    if (!factorValues.isEmpty()) {
                        task.factorData.put(stock.getStockCode(), factorValues);
                    }
                } catch (Exception e) {
                    log.warn("股票 {} 因子计算失败: {}", stock.getStockCode(), e.getMessage());
                } finally {
                    task.completed.incrementAndGet();
                    semaphore.release();
                }
            }));
        }

        // 等待全部完成
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                log.warn("任务执行异常: {}", e.getMessage());
            }
        }
        executor.shutdown();
        task.currentStock.set("");

        if (task.cancelled.get()) {
            markTaskDone(task, null);
            return;
        }

        // 阶段 2: 因子评估 + 因子组合
        try {
            task.totalFactors.set(
                    task.request.getFactorNames() != null ? task.request.getFactorNames().size() : 0);

            List<FactorEvalResult> evalResults = factorEvalService.evalFactors(
                    task.taskId, task.factorData,
                    task.request.getFactorNames(), task.request.getForwardDays(),
                    task.factorCompleted, null);

            String resultsJson = objectMapper.writeValueAsString(evalResults);

            String combinationJson = null;
            try {
                FactorCombinationResult combination = factorCombinationService.combineFactors(
                        task.factorData,
                        task.request.getFactorNames(),
                        task.request.getForwardDays());
                combinationJson = objectMapper.writeValueAsString(combination);
            } catch (Exception e) {
                log.warn("因子组合回归计算失败: {}", e.getMessage());
            }

            final String combJson = combinationJson;
            factorEvalTaskRepository.findByTaskId(task.taskId).ifPresent(entity -> {
                entity.setResultsJson(resultsJson);
                entity.setCombinationJson(combJson);
                entity.setCompleted(true);
                factorEvalTaskRepository.save(entity);
            });

        } catch (Throwable e) {
            log.error("因子评估失败: taskId={}", task.taskId, e);
            factorEvalTaskRepository.findByTaskId(task.taskId).ifPresent(entity -> {
                entity.setCompleted(true);
                entity.setInitError("因子评估失败: " + e.getMessage());
                factorEvalTaskRepository.save(entity);
            });
        } finally {
            markTaskDone(task, null);
            log.info("任务完成: taskId={}, 已处理={}/{}",
                    task.taskId, task.completed.get(), task.total.get());
        }
    }

    private void markTaskDone(EvalTask task, String error) {
        task.completedFlag.set(true);
        if (error != null) {
            factorEvalTaskRepository.findByTaskId(task.taskId).ifPresent(entity -> {
                entity.setCompleted(true);
                entity.setInitError(error);
                factorEvalTaskRepository.save(entity);
            });
        } else if (!task.cancelled.get()) {
            factorEvalTaskRepository.findByTaskId(task.taskId).ifPresent(entity -> {
                entity.setCompleted(true);
                factorEvalTaskRepository.save(entity);
            });
        }
    }

    // ========================= 进度查询 =========================

    public FactorEvalProgress getProgress(String taskId) {
        EvalTask task = tasks.get(taskId);
        if (task != null) {
            task.lastPollTime = System.currentTimeMillis();

            FactorEvalProgress p = new FactorEvalProgress();
            p.setTaskId(task.taskId);
            p.setCurrent(task.completed.get());
            p.setTotal(task.total.get());
            p.setCurrentStock(task.currentStock.get());
            p.setCurrentFactor(task.currentFactor.get());
            p.setCompleted(task.completedFlag.get());
            p.setCancelled(task.cancelled.get());
            p.setFactorCompleted(task.factorCompleted.get());
            p.setTotalFactors(task.totalFactors.get());
            if (task.initError != null) p.setInitError(task.initError);
            return p;
        }

        // 内存中不存在，从 DB 查询
        return factorEvalTaskRepository.findByTaskId(taskId).map(entity -> {
            FactorEvalProgress p = new FactorEvalProgress();
            p.setTaskId(entity.getTaskId());
            int total = entity.getTotalStocks() != null ? entity.getTotalStocks() : 0;
            p.setTotal(total);
            p.setCurrent(total);
            p.setCurrentStock("");
            p.setCompleted(Boolean.TRUE.equals(entity.getCompleted()));
            p.setCancelled(false);
            if (entity.getInitError() != null) p.setInitError(entity.getInitError());
            return p;
        }).orElse(null);
    }

    // ========================= 任务取消 / 删除 =========================

    public boolean cancelTask(String taskId) {
        EvalTask task = tasks.get(taskId);
        if (task == null) return false;
        task.cancelled.set(true);
        return true;
    }

    @Transactional
    public boolean deleteTask(String taskId) {
        Optional<FactorEvalTaskEntity> entityOpt = factorEvalTaskRepository.findByTaskId(taskId);
        if (entityOpt.isEmpty()) return false;
        factorEvalTaskRepository.delete(entityOpt.get());
        tasks.remove(taskId);
        log.info("已删除任务: taskId={}", taskId);
        return true;
    }

    // ========================= 结果查询 =========================

    public List<FactorEvalResult> getResults(String taskId) {
        return factorEvalTaskRepository.findByTaskId(taskId)
                .map(entity -> {
                    String json = entity.getResultsJson();
                    if (json == null || json.isBlank()) return Collections.<FactorEvalResult>emptyList();
                    try {
                        return objectMapper.readValue(json,
                                objectMapper.getTypeFactory()
                                        .constructCollectionType(List.class, FactorEvalResult.class));
                    } catch (JsonProcessingException e) {
                        log.error("解析因子评估结果失败: taskId={}", taskId, e);
                        return Collections.<FactorEvalResult>emptyList();
                    }
                })
                .orElse(null);
    }

    public FactorCombinationResult getCombinationResult(String taskId) {
        return factorEvalTaskRepository.findByTaskId(taskId)
                .map(FactorEvalTaskEntity::getCombinationJson)
                .filter(json -> json != null && !json.isBlank())
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, FactorCombinationResult.class);
                    } catch (Exception e) {
                        log.error("反序列化因子组合结果失败: taskId={}", taskId, e);
                        return null;
                    }
                })
                .orElse(null);
    }

    public List<FactorEvalTaskEntity> getHistoryTasks(String username) {
        return factorEvalTaskRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public String getTaskOwner(String taskId) {
        return factorEvalTaskRepository.findByTaskId(taskId)
                .map(FactorEvalTaskEntity::getUsername).orElse(null);
    }

    public FactorEvalRequest getTaskRequest(String taskId) {
        // 优先从内存获取
        EvalTask task = tasks.get(taskId);
        if (task != null && task.request != null) {
            return task.request;
        }
        // 降级从 DB 还原
        return factorEvalTaskRepository.findByTaskId(taskId).map(entity -> {
            FactorEvalRequest req = new FactorEvalRequest();
            req.setUsername(entity.getUsername());
            req.setStartDate(entity.getStartDate());
            req.setEndDate(entity.getEndDate());
            req.setForwardDays(entity.getForwardDays() != null ? entity.getForwardDays() : 5);
            if (entity.getFactorNames() != null && !entity.getFactorNames().isBlank()) {
                req.setFactorNames(Arrays.asList(entity.getFactorNames().split(",")));
            }
            return req;
        }).orElse(null);
    }

    public Map<String, Map<String, Map<String, Double>>> getFactorData(String taskId) {
        EvalTask task = tasks.get(taskId);
        if (task != null && task.factorData != null && !task.factorData.isEmpty()) {
            return task.factorData;
        }
        return null;
    }

    public List<FactorDefinition> getFactorDefinitions() {
        return FACTOR_DEFINITIONS.stream().map(def -> {
            FactorDefinition fd = new FactorDefinition();
            fd.setFactorName(def.name);
            fd.setFactorLabel(def.label);
            fd.setFactorCategory(def.category);
            fd.setImportant(def.important);
            return fd;
        }).toList();
    }

    // ========================= BarSeries 构建 =========================

    private BarSeries convertToBarSeries(List<DailyQuote> quotes) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();

        List<DailyQuote> sorted = new ArrayList<>(quotes);
        sorted.sort(Comparator.comparing(DailyQuote::getTradeDate));

        for (DailyQuote q : sorted) {
            if (q.getOpen() == null || q.getHigh() == null || q.getLow() == null
                    || q.getClose() == null || q.getVol() == null) {
                continue;
            }

            ZonedDateTime zdt = ZonedDateTime.of(
                    LocalDate.parse(q.getTradeDate(), DATE_FMT),
                    LocalTime.of(15, 0),
                    MARKET_ZONE);

            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(zdt.toInstant())
                    .openPrice(q.getOpen())
                    .highPrice(q.getHigh())
                    .lowPrice(q.getLow())
                    .closePrice(q.getClose())
                    .volume(q.getVol())
                    .build());
        }
        return series;
    }

    // ========================= 统计计算 =========================

    private static double calcSkewness(double[] values) {
        int n = values.length;
        if (n < 3) return 0.0;

        double mean = 0;
        for (double v : values) mean += v;
        mean /= n;

        double m2 = 0, m3 = 0;
        for (double v : values) {
            double d = v - mean;
            m2 += d * d;
            m3 += d * d * d;
        }
        m2 /= n;
        m3 /= n;

        if (m2 == 0) return 0.0;
        double std = Math.sqrt(m2);
        return m3 / (std * std * std);
    }

    private static double calcKurtosis(double[] values) {
        int n = values.length;
        if (n < 4) return 0.0;

        double mean = 0;
        for (double v : values) mean += v;
        mean /= n;

        double m2 = 0, m4 = 0;
        for (double v : values) {
            double d = v - mean;
            m2 += d * d;
            m4 += d * d * d * d;
        }
        m2 /= n;
        m4 /= n;

        if (m2 == 0) return 0.0;
        return m4 / (m2 * m2) - 3.0;
    }

    // ========================= 工具方法 =========================

    /**
     * 从 BarSeries 中提取 bar 对应的日期字符串（yyyyMMdd）。
     */
    private static String extractDateFromBar(BarSeries series, int barIndex) {
        return series.getBar(barIndex).getEndTime()
                .atZone(MARKET_ZONE).toLocalDate()
                .format(DATE_FMT);
    }

    /**
     * 安全除法：分母为 0 或结果为 NaN/Inf 时返回默认值。
     */
    private static double safeDiv(double numerator, double denominator, double defaultVal) {
        if (denominator == 0) return defaultVal;
        double result = numerator / denominator;
        return Double.isNaN(result) || Double.isInfinite(result) ? defaultVal : result;
    }

    /**
     * NaN / Infinity → null，其余原样返回。
     */
    private static Double toNull(double value) {
        return (Double.isNaN(value) || Double.isInfinite(value)) ? null : value;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    // ========================= 内部数据结构 =========================

    private static class EvalTask {
        String taskId;
        AtomicInteger total;
        AtomicInteger completed;
        AtomicBoolean cancelled;
        AtomicReference<String> currentStock;
        AtomicReference<String> currentFactor;
        AtomicBoolean completedFlag;
        volatile String initError;
        volatile long lastPollTime;
        FactorEvalRequest request;
        ConcurrentHashMap<String, Map<String, Map<String, Double>>> factorData;
        AtomicInteger factorCompleted;
        AtomicInteger totalFactors;
    }

    public static class FactorDef {
        private final String name;
        private final String label;
        private final String category;
        private final boolean important;

        public FactorDef(String name, String label, String category, boolean important) {
            this.name = name;
            this.label = label;
            this.category = category;
            this.important = important;
        }

        public String getName()       { return name; }
        public String getLabel()      { return label; }
        public String getCategory()   { return category; }
        public boolean isImportant()  { return important; }
    }
}
