package com.tradingx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingx.model.FactorEvalResult;
import com.tradingx.model.FactorEvalTaskEntity;
import com.tradingx.repository.FactorEvalTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tradingx.service.wavelet.WaveletDenoiser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FactorEvalService {

    private static final Logger log = LoggerFactory.getLogger(FactorEvalService.class);

    private final FactorEvalTaskRepository factorEvalTaskRepository;
    private final ObjectMapper objectMapper;

    /** 截面最小有效样本量，低于此值跳过当日 IC 与分层计算 */
    private static final int MIN_CROSS_SECTION_SIZE = 20;

    /** 分层数量 */
    private static final int LAYER_COUNT = 5;

    /**
     * pct_chg 缩放因子。
     * 若 pct_chg 以百分比存储（如 2.5 表示 +2.5%），设为 100.0；
     * 若以小数存储（如 0.025 表示 +2.5%），设为 1.0。
     */
    private static final double PCT_CHG_DIVISOR = 100.0;

    private static final Map<String, String[]> FACTOR_META = Map.ofEntries(
            Map.entry("return_rate", new String[]{"收益率", "price"}),
            Map.entry("amplitude", new String[]{"振幅", "price"}),
            Map.entry("gap", new String[]{"跳空缺口", "price"}),
            Map.entry("high_low_dist", new String[]{"新高新低距离", "price"}),
            Map.entry("sma5_sma20", new String[]{"SMA5/SMA20", "ma"}),
            Map.entry("sma10_sma30", new String[]{"SMA10/SMA30", "ma"}),
            Map.entry("ema12_ema26", new String[]{"EMA12/EMA26", "ma"}),
            Map.entry("rsi_14", new String[]{"RSI(14)", "oscillator"}),
            Map.entry("macd_hist", new String[]{"MACD柱", "oscillator"}),
            Map.entry("kdj_k", new String[]{"KDJ-K", "oscillator"}),
            Map.entry("cci_14", new String[]{"CCI(14)", "oscillator"}),
            Map.entry("wr_14", new String[]{"WR(14)", "oscillator"}),
            Map.entry("roc_10", new String[]{"ROC(10)", "oscillator"}),
            Map.entry("atr_14", new String[]{"ATR(14)", "volatility"}),
            Map.entry("boll_position", new String[]{"布林带位置", "volatility"}),
            Map.entry("std_20", new String[]{"标准差(20)", "volatility"}),
            Map.entry("turnover", new String[]{"换手率", "volume"}),
            Map.entry("volume_ratio", new String[]{"量比", "volume"}),
            Map.entry("obv", new String[]{"OBV", "volume"}),
            Map.entry("skewness_20", new String[]{"偏度(20)", "stats"}),
            Map.entry("kurtosis_20", new String[]{"峰度(20)", "stats"})
    );

    public FactorEvalService(FactorEvalTaskRepository factorEvalTaskRepository) {
        this.factorEvalTaskRepository = factorEvalTaskRepository;
        this.objectMapper = new ObjectMapper();
    }

    // ============================== 主入口 ==============================

    public List<FactorEvalResult> evalFactors(String taskId,
                                               Map<String, Map<String, Map<String, Double>>> allFactorValues,
                                               List<String> factorNames,
                                               int forwardDays,
                                               AtomicInteger factorCompleted,
                                               String[] currentFactor) {

        // 1. 收集所有交易日（TreeSet 自动排序去重）
        TreeSet<String> allDates = new TreeSet<>();
        for (Map<String, Map<String, Double>> stockData : allFactorValues.values()) {
            allDates.addAll(stockData.keySet());
        }
        List<String> sortedDates = new ArrayList<>(allDates);

        log.info("因子评估开始: factors={}, stocks={}, dates={}, forwardDays={}",
                factorNames.size(), allFactorValues.size(), sortedDates.size(), forwardDays);

        // 2. 预计算前瞻收益率（复利）
        long t0 = System.currentTimeMillis();
        Map<String, Map<String, Double>> forwardReturnCache =
                precomputeForwardReturns(allFactorValues, sortedDates, forwardDays);
        log.info("前瞻收益率缓存完成: stocks={}, 耗时={}ms",
                forwardReturnCache.size(), System.currentTimeMillis() - t0);

        // 3. 逐因子评估
        List<FactorEvalResult> results = new ArrayList<>();
        for (String factorName : factorNames) {
            if (currentFactor != null && currentFactor.length > 0) {
                currentFactor[0] = factorName;
            }

            FactorEvalResult result = evaluateSingleFactor(
                    factorName, allFactorValues, forwardReturnCache, sortedDates);
            results.add(result);

            if (factorCompleted != null) {
                factorCompleted.incrementAndGet();
            }
        }

        // 4. 按 ICIR 降序排序
        results.sort((a, b) -> Double.compare(b.getIcir(), a.getIcir()));
        return results;
    }

    // ========================== 单因子评估 ==========================

    private FactorEvalResult evaluateSingleFactor(
            String factorName,
            Map<String, Map<String, Map<String, Double>>> allFactorValues,
            Map<String, Map<String, Double>> forwardReturnCache,
            List<String> sortedDates) {

        FactorEvalResult result = new FactorEvalResult();
        result.setFactorName(factorName);

        // 因子元信息
        String[] meta = FACTOR_META.get(factorName);
        if (meta != null) {
            result.setFactorLabel(meta[0]);
            result.setFactorCategory(meta[1]);
        }

        // IC 序列
        List<Double> icSeries = new ArrayList<>(sortedDates.size());
        List<Double> pearsonIcSeries = new ArrayList<>(sortedDates.size());

        // 统计计数
        int totalPairs = 0;
        int nonNullPairs = 0;

        // 分层收益累加器（原始数组，避免每个日期创建 List）
        double[] layerSum = new double[LAYER_COUNT + 1];   // index 1..LAYER_COUNT
        int[] layerCnt = new int[LAYER_COUNT + 1];

        // 预分配截面缓冲区（每因子只分配一次，所有日期复用）
        int maxStocks = allFactorValues.size();
        double[] fBuf = new double[maxStocks];              // 因子值
        double[] rBuf = new double[maxStocks];              // 前瞻收益
        Integer[] sortIdx = new Integer[maxStocks];         // 排序索引
        for (int i = 0; i < maxStocks; i++) sortIdx[i] = i;
        double[] rankBuf1 = new double[maxStocks];          // 因子秩缓冲
        double[] rankBuf2 = new double[maxStocks];          // 收益秩缓冲

        // 逐日截面计算
        for (String currentDate : sortedDates) {
            int bufLen = 0;

            for (Map.Entry<String, Map<String, Map<String, Double>>> entry : allFactorValues.entrySet()) {
                Map<String, Map<String, Double>> stockData = entry.getValue();
                Map<String, Double> dateData = stockData.get(currentDate);
                if (dateData == null) continue;

                Double factorVal = dateData.get(factorName);
                Map<String, Double> stockReturns = forwardReturnCache.get(entry.getKey());
                Double forwardReturn = (stockReturns != null) ? stockReturns.get(currentDate) : null;

                totalPairs++;
                if (isValidDouble(factorVal) && isValidDouble(forwardReturn)) {
                    nonNullPairs++;
                    fBuf[bufLen] = factorVal;
                    rBuf[bufLen] = forwardReturn;
                    bufLen++;
                }
            }

            if (bufLen >= MIN_CROSS_SECTION_SIZE) {
                winsorize(fBuf, bufLen);
                zScoreNormalize(fBuf, bufLen);

                // 当日截面 Rank IC
                Double ic = calcRankIC(fBuf, rBuf, bufLen, sortIdx, rankBuf1, rankBuf2);
                if (ic != null) {
                    icSeries.add(ic);
                }

                // 当日截面 Pearson IC
                Double pic = calcPearsonIC(fBuf, rBuf, bufLen);
                if (pic != null) {
                    pearsonIcSeries.add(pic);
                }

                // 分层回测
                accumulateLayerReturns(fBuf, rBuf, bufLen, layerSum, layerCnt, sortIdx);
            }
        }

        // ---- 汇总 IC 统计量 ----
        computeICStatistics(result, icSeries);

        // ---- 汇总 Pearson IC 统计量 ----
        computePearsonICStatistics(result, pearsonIcSeries);

        // 覆盖率
        result.setCoverage(totalPairs > 0 ? (double) nonNullPairs / totalPairs : 0.0);

        // ---- 汇总分层收益 ----
        Map<Integer, Double> layerReturns = new LinkedHashMap<>();
        for (int i = 1; i <= LAYER_COUNT; i++) {
            layerReturns.put(i, layerCnt[i] > 0 ? layerSum[i] / layerCnt[i] : 0.0);
        }
        try {
            result.setLayerReturnsJson(objectMapper.writeValueAsString(layerReturns));
        } catch (Exception e) {
            log.error("序列化分层收益失败: factorName={}", factorName, e);
            result.setLayerReturnsJson("{}");
        }

        log.info("因子评估完成: factor={}, icDates={}, pairs={}/{}, icMean={}, icir={}",
                factorName, icSeries.size(), nonNullPairs, totalPairs,
                formatDouble(result.getIcMean()), formatDouble(result.getIcir()));

        return result;
    }

    // ======================== IC 统计量计算 ========================

    private void computeICStatistics(FactorEvalResult result, List<Double> icSeries) {
        if (icSeries.isEmpty()) return;

        int n = icSeries.size();

        // IC 衰减加权：半衰期 = n，近期 IC 权重略高
        double halfLife = Math.max((double) n, 10.0);
        double decayFactor = Math.log(2.0) / halfLife;

        double weightSum = 0;
        double weightedIcSum = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.exp(decayFactor * i);
            weightSum += w;
            weightedIcSum += w * icSeries.get(i);
        }
        double icMean = weightedIcSum / weightSum;

        double weightedVarSum = 0;
        long positiveCount = 0;
        for (int i = 0; i < n; i++) {
            double ic = icSeries.get(i);
            double w = Math.exp(decayFactor * i);
            double diff = ic - icMean;
            weightedVarSum += w * diff * diff;
            if (ic > 0) positiveCount++;
        }
        double icStd = Math.sqrt(weightedVarSum / weightSum);
        double icir = icStd > 1e-12 ? icMean / icStd : 0.0;

        result.setIcMean(icMean);
        result.setIcStd(icStd);
        result.setIcir(icir);
        result.setIcWinRate((double) positiveCount / n);
    }

    private void computePearsonICStatistics(FactorEvalResult result, List<Double> pearsonIcSeries) {
        if (pearsonIcSeries.isEmpty()) return;

        int n = pearsonIcSeries.size();
        double halfLife = Math.max((double) n, 10.0);
        double decayFactor = Math.log(2.0) / halfLife;

        double weightSum = 0;
        double weightedIcSum = 0;
        for (int i = 0; i < n; i++) {
            double w = Math.exp(decayFactor * i);
            weightSum += w;
            weightedIcSum += w * pearsonIcSeries.get(i);
        }
        double pearsonIcMean = weightedIcSum / weightSum;

        double weightedVarSum = 0;
        for (int i = 0; i < n; i++) {
            double ic = pearsonIcSeries.get(i);
            double w = Math.exp(decayFactor * i);
            double diff = ic - pearsonIcMean;
            weightedVarSum += w * diff * diff;
        }
        double pearsonIcStd = Math.sqrt(weightedVarSum / weightSum);
        double pearsonIcir = pearsonIcStd > 1e-12 ? pearsonIcMean / pearsonIcStd : 0.0;

        result.setPearsonIcMean(pearsonIcMean);
        result.setPearsonIcir(pearsonIcir);
    }

    // ======================== 分层收益计算 ========================

    /**
     * 将截面股票按因子值分层，累加各层的前瞻收益。
     * sortIdx 在调用前已预填充 [0, 1, ..., maxStocks-1]，
     * 此处仅对其 [0, len) 范围做排序，不会污染后续调用。
     */
    private void accumulateLayerReturns(double[] factorBuf, double[] returnBuf, int len,
                                         double[] layerSum, int[] layerCnt,
                                         Integer[] sortIdx) {
        // 对 [0, len) 范围的索引按因子值升序排序
        Arrays.sort(sortIdx, 0, len, (a, b) -> Double.compare(factorBuf[a], factorBuf[b]));

        int layerSize = (int) Math.ceil((double) len / LAYER_COUNT);
        for (int layer = 1; layer <= LAYER_COUNT; layer++) {
            int start = (layer - 1) * layerSize;
            int end = Math.min(start + layerSize, len);
            double sum = 0;
            for (int j = start; j < end; j++) {
                sum += returnBuf[sortIdx[j]];
            }
            layerSum[layer] += sum;
            layerCnt[layer] += (end - start);
        }
    }

    // ======================== Rank IC 计算 ========================

    /**
     * 基于原始数组的 Rank IC（Spearman 秩相关系数）。
     * 复用外部传入的排序索引和秩缓冲区，避免在热路径中反复分配对象。
     *
     * @param factorValues 因子值数组
     * @param returns      前瞻收益数组
     * @param len          有效数据长度（[0, len)）
     * @param sortIdx      预分配的排序索引缓冲（长度 >= len）
     * @param rankBuf1     预分配的因子秩缓冲（长度 >= len）
     * @param rankBuf2     预分配的收益秩缓冲（长度 >= len）
     * @return Rank IC 值，数据不足时返回 null
     */
    public Double calcRankIC(double[] factorValues, double[] returns, int len,
                              Integer[] sortIdx, double[] rankBuf1, double[] rankBuf2) {
        if (len < MIN_CROSS_SECTION_SIZE) return null;

        // 计算因子值的秩
        rankArray(factorValues, len, sortIdx, rankBuf1);
        // 计算收益的秩
        rankArray(returns, len, sortIdx, rankBuf2);

        // Pearson 相关（对秩向量）
        double meanF = 0, meanR = 0;
        for (int i = 0; i < len; i++) {
            meanF += rankBuf1[i];
            meanR += rankBuf2[i];
        }
        meanF /= len;
        meanR /= len;

        double cov = 0, varF = 0, varR = 0;
        for (int i = 0; i < len; i++) {
            double df = rankBuf1[i] - meanF;
            double dr = rankBuf2[i] - meanR;
            cov += df * dr;
            varF += df * df;
            varR += dr * dr;
        }

        double denom = Math.sqrt(varF * varR);
        return denom < 1e-15 ? 0.0 : cov / denom;
    }

    /**
     * List 版 Rank IC，保持对外 API 兼容（适用于小规模调用）。
     */
    public Double calcRankIC(List<Double> factorValues, List<Double> returns) {
        int n = factorValues.size();
        double[] fArr = new double[n];
        double[] rArr = new double[n];
        for (int i = 0; i < n; i++) {
            fArr[i] = factorValues.get(i);
            rArr[i] = returns.get(i);
        }

        Integer[] sortIdx = new Integer[n];
        for (int i = 0; i < n; i++) sortIdx[i] = i;
        double[] rankBuf1 = new double[n];
        double[] rankBuf2 = new double[n];

        return calcRankIC(fArr, rArr, n, sortIdx, rankBuf1, rankBuf2);
    }

    /**
     * Pearson IC（普通相关系数）。
     * 在 Z-score 标准化后，因子近似正态，Pearson IC 有意义。
     */
    public Double calcPearsonIC(double[] factorValues, double[] returns, int len) {
        if (len < MIN_CROSS_SECTION_SIZE) return null;

        double meanF = 0, meanR = 0;
        for (int i = 0; i < len; i++) {
            meanF += factorValues[i];
            meanR += returns[i];
        }
        meanF /= len;
        meanR /= len;

        double cov = 0, varF = 0, varR = 0;
        for (int i = 0; i < len; i++) {
            double df = factorValues[i] - meanF;
            double dr = returns[i] - meanR;
            cov += df * dr;
            varF += df * df;
            varR += dr * dr;
        }

        double denom = Math.sqrt(varF * varR);
        return denom < 1e-15 ? 0.0 : cov / denom;
    }

    /**
     * 对 values[0..len) 计算秩（并列值取平均秩），结果写入 rankOut[0..len)。
     * sortIdx 作为临时排序缓冲被复用。
     */
    private void rankArray(double[] values, int len, Integer[] sortIdx, double[] rankOut) {
        // 初始化索引
        for (int i = 0; i < len; i++) sortIdx[i] = i;
        // 按 values 升序排列索引
        Arrays.sort(sortIdx, 0, len, (a, b) -> Double.compare(values[a], values[b]));

        // 计算平均秩
        int i = 0;
        while (i < len) {
            int j = i;
            // 检测并列值
            while (j < len - 1
                    && Double.compare(values[sortIdx[j + 1]], values[sortIdx[j]]) == 0) {
                j++;
            }
            double avgRank = (i + j + 2) / 2.0;   // rank from 1
            for (int k = i; k <= j; k++) {
                rankOut[sortIdx[k]] = avgRank;
            }
            i = j + 1;
        }
    }

    // ==================== 前瞻收益率预计算 ====================

    /**
     * 为每只股票预计算每个日期的 N 日复利前瞻收益率。
     * 复利公式: R = (1 + r1) * (1 + r2) * ... * (1 + rN) - 1
     */
    private Map<String, Map<String, Double>> precomputeForwardReturns(
            Map<String, Map<String, Map<String, Double>>> allFactorValues,
            List<String> sortedDates,
            int forwardDays) {

        Map<String, Map<String, Double>> cache = new HashMap<>();

        for (Map.Entry<String, Map<String, Map<String, Double>>> entry : allFactorValues.entrySet()) {
            String tsCode = entry.getKey();
            Map<String, Map<String, Double>> stockData = entry.getValue();

            // 按日期排序（股票可能有缺失交易日）
            List<String> stockDates = new ArrayList<>(stockData.keySet());
            Collections.sort(stockDates);
            int dateCount = stockDates.size();

            Map<String, Double> dateReturns = new HashMap<>();

            for (int i = 0; i + forwardDays < dateCount; i++) {
                double compoundReturn = 0.0;
                boolean valid = true;

                for (int j = 1; j <= forwardDays; j++) {
                    Map<String, Double> futureData = stockData.get(stockDates.get(i + j));
                    if (futureData == null) {
                        valid = false;
                        break;
                    }
                    Double pctChg = futureData.get("pct_chg");
                    if (!isValidDouble(pctChg)) {
                        valid = false;
                        break;
                    }
                    // 复利计算
                    double dailyReturn = pctChg / PCT_CHG_DIVISOR;
                    compoundReturn = (1.0 + compoundReturn) * (1.0 + dailyReturn) - 1.0;
                }

                if (valid) {
                    dateReturns.put(stockDates.get(i), compoundReturn);
                }
            }

            cache.put(tsCode, dateReturns);
        }

        return cache;
    }

    // ======================== 持久化 ========================

    public void saveResults(String taskId, List<FactorEvalResult> results) {
        factorEvalTaskRepository.findByTaskId(taskId).ifPresent(entity -> {
            try {
                entity.setResultsJson(objectMapper.writeValueAsString(results));
                entity.setCompleted(true);
                factorEvalTaskRepository.save(entity);
            } catch (Exception e) {
                log.error("保存因子评估结果失败: taskId={}", taskId, e);
            }
        });
    }

    // ======================== 小波去噪 ========================

    private static final WaveletDenoiser WAVELET_DENOISER = new WaveletDenoiser();

    /**
     * 对每只股票的每个因子时序做 db4 小波去噪。
     * 返回新的数据结构，不修改原始 allFactorValues。
     * pct_chg 不做去噪（用于前瞻收益计算）。
     */
    private Map<String, Map<String, Map<String, Double>>> denoiseAllFactors(
            Map<String, Map<String, Map<String, Double>>> allFactorValues,
            List<String> factorNames) {

        Map<String, Map<String, Map<String, Double>>> result = new HashMap<>();

        for (Map.Entry<String, Map<String, Map<String, Double>>> stockEntry : allFactorValues.entrySet()) {
            String tsCode = stockEntry.getKey();
            Map<String, Map<String, Double>> stockData = stockEntry.getValue();

            // 收集每个因子的 日期→值 时序
            Map<String, Map<String, Double>> denoisedStockData = new HashMap<>();

            for (String factorName : factorNames) {
                Map<String, Double> factorSeries = new LinkedHashMap<>();
                for (Map.Entry<String, Map<String, Double>> dateEntry : stockData.entrySet()) {
                    Double val = dateEntry.getValue().get(factorName);
                    if (val != null) {
                        factorSeries.put(dateEntry.getKey(), val);
                    }
                }

                if (factorSeries.size() >= 16) {
                    Map<String, Double> denoised = WAVELET_DENOISER.denoiseFactorMap(factorSeries);
                    for (Map.Entry<String, Double> e : denoised.entrySet()) {
                        denoisedStockData.computeIfAbsent(e.getKey(), k -> new HashMap<>())
                                .put(factorName, e.getValue());
                    }
                } else {
                    for (Map.Entry<String, Double> e : factorSeries.entrySet()) {
                        denoisedStockData.computeIfAbsent(e.getKey(), k -> new HashMap<>())
                                .put(factorName, e.getValue());
                    }
                }
            }

            // 保留 pct_chg（前瞻收益计算需要，不做去噪）
            for (Map.Entry<String, Map<String, Double>> dateEntry : stockData.entrySet()) {
                Double pctChg = dateEntry.getValue().get("pct_chg");
                if (pctChg != null) {
                    denoisedStockData.computeIfAbsent(dateEntry.getKey(), k -> new HashMap<>())
                            .put("pct_chg", pctChg);
                }
            }

            result.put(tsCode, denoisedStockData);
        }

        return result;
    }

    // ======================== 截面标准化 ========================

    /**
     * MAD 去极值：将超过 5 倍中位数绝对偏差的值截断。
     * 原地修改 values[0..len)。
     */
    private void winsorize(double[] values, int len) {
        double[] copy = new double[len];
        System.arraycopy(values, 0, copy, 0, len);
        Arrays.sort(copy);
        double median = copy[len / 2];

        double[] absDevs = new double[len];
        for (int i = 0; i < len; i++) {
            absDevs[i] = Math.abs(values[i] - median);
        }
        Arrays.sort(absDevs);
        double mad = absDevs[len / 2];
        double bound = 5 * 1.4826 * mad;

        if (bound < 1e-12) return;

        double lower = median - bound;
        double upper = median + bound;
        for (int i = 0; i < len; i++) {
            if (values[i] < lower) values[i] = lower;
            else if (values[i] > upper) values[i] = upper;
        }
    }

    /**
     * Z-score 截面标准化：(x - mean) / std。
     * 原地修改 values[0..len)。
     */
    private void zScoreNormalize(double[] values, int len) {
        double mean = 0;
        for (int i = 0; i < len; i++) mean += values[i];
        mean /= len;

        double variance = 0;
        for (int i = 0; i < len; i++) {
            double diff = values[i] - mean;
            variance += diff * diff;
        }
        variance /= len;

        double std = Math.sqrt(variance);
        if (std < 1e-12) return;

        for (int i = 0; i < len; i++) {
            values[i] = (values[i] - mean) / std;
        }
    }

    // ======================== 工具方法 ========================

    private static boolean isValidDouble(Double value) {
        return value != null && !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String formatDouble(double value) {
        return String.format("%.6f", value);
    }
}
