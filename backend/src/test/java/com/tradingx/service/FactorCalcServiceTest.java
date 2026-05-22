package com.tradingx.service;

import com.tradingx.model.DailyQuote;
import com.tradingx.model.FactorEvalResult;
import com.tradingx.repository.FactorEvalTaskRepository;
import com.tradingx.repository.StockListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNumFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FactorCalcServiceTest {

    private FactorCalcService factorCalcService;
    private KlineService klineService;
    private StockListRepository stockListRepository;
    private FactorEvalTaskRepository factorEvalTaskRepository;
    private FactorEvalService factorEvalService;
    private FactorCombinationService factorCombinationService;

    @BeforeEach
    void setUp() {
        klineService = mock(KlineService.class);
        stockListRepository = mock(StockListRepository.class);
        factorEvalTaskRepository = mock(FactorEvalTaskRepository.class);
        factorEvalService = new FactorEvalService(factorEvalTaskRepository);
        factorCombinationService = new FactorCombinationService();

        factorCalcService = new FactorCalcService(
                klineService, stockListRepository, factorEvalTaskRepository, factorEvalService, factorCombinationService);
    }

    private List<DailyQuote> generateTestQuotes(String tsCode, int count, String startDate) {
        List<DailyQuote> quotes = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        ZonedDateTime date = ZonedDateTime.of(
                java.time.LocalDate.parse(startDate, formatter),
                java.time.LocalTime.of(15, 0),
                ZoneId.systemDefault()
        );

        double basePrice = 10.0;
        java.util.Random random = new java.util.Random(42);

        for (int i = 0; i < count; i++) {
            while (date.getDayOfWeek().getValue() > 5) {
                date = date.plusDays(1);
            }

            DailyQuote q = new DailyQuote();
            q.setTsCode(tsCode);
            q.setTradeDate(date.format(formatter));

            double change = (random.nextDouble() - 0.48) * 0.06;
            basePrice = basePrice * (1 + change);
            double open = basePrice * (1 + (random.nextDouble() - 0.5) * 0.02);
            double high = Math.max(open, basePrice) * (1 + random.nextDouble() * 0.02);
            double low = Math.min(open, basePrice) * (1 - random.nextDouble() * 0.02);
            double vol = 10000 + random.nextDouble() * 50000;

            q.setOpen(BigDecimal.valueOf(open).setScale(2, BigDecimal.ROUND_HALF_UP));
            q.setHigh(BigDecimal.valueOf(high).setScale(2, BigDecimal.ROUND_HALF_UP));
            q.setLow(BigDecimal.valueOf(low).setScale(2, BigDecimal.ROUND_HALF_UP));
            q.setClose(BigDecimal.valueOf(basePrice).setScale(2, BigDecimal.ROUND_HALF_UP));
            q.setPreClose(BigDecimal.valueOf(basePrice / (1 + change)).setScale(2, BigDecimal.ROUND_HALF_UP));
            q.setChange(BigDecimal.valueOf(basePrice - basePrice / (1 + change)).setScale(2, BigDecimal.ROUND_HALF_UP));
            q.setPctChg(BigDecimal.valueOf(change * 100).setScale(2, BigDecimal.ROUND_HALF_UP));
            q.setVol(BigDecimal.valueOf(vol).setScale(2, BigDecimal.ROUND_HALF_UP));
            q.setAmount(BigDecimal.valueOf(basePrice * vol).setScale(2, BigDecimal.ROUND_HALF_UP));

            quotes.add(q);
            date = date.plusDays(1);
        }

        return quotes;
    }

    @Test
    void calcFactorValues_shouldNotThrowException() {
        List<DailyQuote> quotes = generateTestQuotes("000001.SZ", 300, "20230101");
        when(klineService.getKlineData("000001.SZ", "day")).thenReturn(quotes);
        when(stockListRepository.findByStockCode("000001.SZ")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> {
            Map<String, Map<String, Double>> result =
                    factorCalcService.calcFactorValues("000001.SZ", "20240101", "20241231", 5);
            assertNotNull(result);
            assertFalse(result.isEmpty());
        });
    }

    @Test
    void calcFactorValues_amplitudeAtFirstBar_shouldNotThrowIndexOutOfBounds() {
        List<DailyQuote> quotes = generateTestQuotes("000001.SZ", 50, "20240101");
        when(klineService.getKlineData("000001.SZ", "day")).thenReturn(quotes);
        when(stockListRepository.findByStockCode("000001.SZ")).thenReturn(Optional.empty());

        Map<String, Map<String, Double>> result =
                factorCalcService.calcFactorValues("000001.SZ", "20240101", "20240301", 5);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        String firstDate = result.keySet().iterator().next();
        Map<String, Double> firstValues = result.get(firstDate);
        assertNotNull(firstValues.get("amplitude"));
        assertEquals(0.0, firstValues.get("amplitude"));
    }

    @Test
    void calcFactorValues_shouldProduceNonZeroResults() {
        List<DailyQuote> quotes = generateTestQuotes("000001.SZ", 300, "20230101");
        when(klineService.getKlineData("000001.SZ", "day")).thenReturn(quotes);
        when(stockListRepository.findByStockCode("000001.SZ")).thenReturn(Optional.empty());

        Map<String, Map<String, Double>> result =
                factorCalcService.calcFactorValues("000001.SZ", "20240101", "20241231", 5);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        boolean hasNonZeroReturn = false;
        boolean hasNonZeroRsi = false;
        boolean hasNonZeroMacd = false;

        for (Map<String, Double> factors : result.values()) {
            Double returnRate = factors.get("return_rate");
            Double rsi = factors.get("rsi_14");
            Double macdHist = factors.get("macd_hist");

            if (returnRate != null && returnRate != 0.0) hasNonZeroReturn = true;
            if (rsi != null && rsi != 0.0 && !Double.isNaN(rsi)) hasNonZeroRsi = true;
            if (macdHist != null && macdHist != 0.0 && !Double.isNaN(macdHist)) hasNonZeroMacd = true;
        }

        assertTrue(hasNonZeroReturn, "return_rate should have non-zero values");
        assertTrue(hasNonZeroRsi, "rsi_14 should have non-zero values");
        assertTrue(hasNonZeroMacd, "macd_hist should have non-zero values");
    }

    @Test
    void calcFactorValues_shouldNotContainNaNValues() {
        List<DailyQuote> quotes = generateTestQuotes("000001.SZ", 300, "20230101");
        when(klineService.getKlineData("000001.SZ", "day")).thenReturn(quotes);
        when(stockListRepository.findByStockCode("000001.SZ")).thenReturn(Optional.empty());

        Map<String, Map<String, Double>> result =
                factorCalcService.calcFactorValues("000001.SZ", "20240101", "20241231", 5);

        for (Map.Entry<String, Map<String, Double>> dateEntry : result.entrySet()) {
            for (Map.Entry<String, Double> factorEntry : dateEntry.getValue().entrySet()) {
                if (factorEntry.getValue() != null) {
                    assertFalse(Double.isNaN(factorEntry.getValue()),
                            "NaN found: date=" + dateEntry.getKey() + " factor=" + factorEntry.getKey());
                    assertFalse(Double.isInfinite(factorEntry.getValue()),
                            "Infinite found: date=" + dateEntry.getKey() + " factor=" + factorEntry.getKey());
                }
            }
        }
    }

    @Test
    void calcFactorValues_withNullPriceFields_shouldNotThrow() {
        List<DailyQuote> quotes = generateTestQuotes("000001.SZ", 100, "20240101");
        quotes.get(5).setOpen(null);
        quotes.get(10).setClose(null);
        quotes.get(15).setVol(null);

        when(klineService.getKlineData("000001.SZ", "day")).thenReturn(quotes);
        when(stockListRepository.findByStockCode("000001.SZ")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> {
            Map<String, Map<String, Double>> result =
                    factorCalcService.calcFactorValues("000001.SZ", "20240101", "20240601", 5);
            assertNotNull(result);
        });
    }

    @Test
    void calcFactorValues_emptyData_shouldReturnEmptyMap() {
        when(klineService.getKlineData("999999.SZ", "day")).thenReturn(Collections.emptyList());

        Map<String, Map<String, Double>> result =
                factorCalcService.calcFactorValues("999999.SZ", "20240101", "20241231", 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void calcFactorValues_warmupData_shouldBeIncluded() {
        List<DailyQuote> quotes = generateTestQuotes("000001.SZ", 500, "20230101");
        when(klineService.getKlineData("000001.SZ", "day")).thenReturn(quotes);
        when(stockListRepository.findByStockCode("000001.SZ")).thenReturn(Optional.empty());

        Map<String, Map<String, Double>> result =
                factorCalcService.calcFactorValues("000001.SZ", "20240601", "20241231", 5);

        assertNotNull(result);
        assertFalse(result.isEmpty());

        for (String date : result.keySet()) {
            assertTrue(date.compareTo("20240601") >= 0,
                    "Output should only contain dates from startDate onwards, got: " + date);
        }
    }

    @Test
    void factorEvalService_calcRankIC_withValidData() {
        List<Double> factors = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5);
        List<Double> returns = Arrays.asList(0.01, 0.02, 0.03, 0.04, 0.05);

        Double ic = factorEvalService.calcRankIC(factors, returns);

        assertNotNull(ic);
        assertTrue(ic > 0, "Perfect positive rank correlation should be positive");
    }

    @Test
    void factorEvalService_calcRankIC_withNaNValues() {
        List<Double> factors = Arrays.asList(0.1, Double.NaN, 0.3, 0.4, 0.5);
        List<Double> returns = Arrays.asList(0.01, 0.02, 0.03, Double.NaN, 0.05);

        Double ic = factorEvalService.calcRankIC(factors, returns);

        assertNotNull(ic, "calcRankIC should handle NaN values gracefully");
    }

    @Test
    void factorEvalService_calcRankIC_withNullValues() {
        List<Double> factors = Arrays.asList(0.1, null, 0.3, 0.4, 0.5);
        List<Double> returns = Arrays.asList(0.01, 0.02, null, 0.04, 0.05);

        Double ic = factorEvalService.calcRankIC(factors, returns);

        assertNotNull(ic, "calcRankIC should handle null values gracefully");
    }

    @Test
    void factorEvalService_evalFactors_withSufficientForwardData() {
        Map<String, Map<String, Map<String, Double>>> allFactorValues = new HashMap<>();

        for (String stock : new String[]{"000001.SZ", "000002.SZ", "000003.SZ"}) {
            Map<String, Map<String, Double>> stockData = new LinkedHashMap<>();
            for (int d = 1; d <= 30; d++) {
                String date = "202401" + String.format("%02d", d);
                Map<String, Double> dayData = new LinkedHashMap<>();
                dayData.put("pct_chg", 1.0);
                dayData.put("rsi_14", 50.0);
                stockData.put(date, dayData);
            }
            allFactorValues.put(stock, stockData);
        }

        List<FactorEvalResult> results = factorEvalService.evalFactors(
                "test-forward", allFactorValues,
                Arrays.asList("rsi_14"), 5, null, null);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertTrue(results.get(0).getCoverage() > 0, "Should have some coverage with sufficient data");
    }

    @Test
    void factorEvalService_evalFactors_withInsufficientForwardData() {
        Map<String, Map<String, Map<String, Double>>> allFactorValues = new HashMap<>();

        for (String stock : new String[]{"000001.SZ", "000002.SZ", "000003.SZ"}) {
            Map<String, Map<String, Double>> stockData = new LinkedHashMap<>();
            for (int d = 1; d <= 3; d++) {
                String date = "2024010" + d;
                Map<String, Double> dayData = new LinkedHashMap<>();
                dayData.put("pct_chg", 1.0);
                dayData.put("rsi_14", 50.0);
                stockData.put(date, dayData);
            }
            allFactorValues.put(stock, stockData);
        }

        List<FactorEvalResult> results = factorEvalService.evalFactors(
                "test-insufficient", allFactorValues,
                Arrays.asList("rsi_14"), 5, null, null);

        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void factorEvalService_evalFactors_withMultipleStocks() {
        Map<String, Map<String, Map<String, Double>>> allFactorValues = new HashMap<>();

        String[] stocks = {"000001.SZ", "000002.SZ", "000003.SZ", "000004.SZ", "000005.SZ"};
        java.util.Random random = new java.util.Random(42);

        for (String stock : stocks) {
            Map<String, Map<String, Double>> stockData = new LinkedHashMap<>();
            for (int d = 1; d <= 30; d++) {
                String date = "202401" + String.format("%02d", d);
                Map<String, Double> dayData = new LinkedHashMap<>();
                dayData.put("pct_chg", (random.nextDouble() - 0.5) * 10);
                dayData.put("rsi_14", 30 + random.nextDouble() * 40);
                dayData.put("macd_hist", (random.nextDouble() - 0.5) * 2);
                dayData.put("return_rate", (random.nextDouble() - 0.5) * 0.1);
                stockData.put(date, dayData);
            }
            allFactorValues.put(stock, stockData);
        }

        List<FactorEvalResult> results = factorEvalService.evalFactors(
                "test-task", allFactorValues,
                Arrays.asList("rsi_14", "macd_hist", "return_rate"), 5, null, null);

        assertNotNull(results);
        assertEquals(3, results.size());

        for (FactorEvalResult r : results) {
            assertNotNull(r.getFactorName());
            assertNotNull(r.getFactorLabel());
            assertNotNull(r.getFactorCategory());
            assertTrue(r.getCoverage() >= 0 && r.getCoverage() <= 1,
                    "Coverage should be between 0 and 1, got: " + r.getCoverage());
        }
    }

    @Test
    void ta4jBarSeries_rsiIndicator_shouldNotReturnNaNAfterWarmup() {
        BarSeries series = new BaseBarSeriesBuilder()
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();

        java.util.Random random = new java.util.Random(42);
        double price = 10.0;
        ZonedDateTime zdt = ZonedDateTime.of(2024, 1, 2, 15, 0, 0, 0, ZoneId.systemDefault());

        for (int i = 0; i < 100; i++) {
            while (zdt.getDayOfWeek().getValue() > 5) {
                zdt = zdt.plusDays(1);
            }

            price *= (1 + (random.nextDouble() - 0.48) * 0.06);
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(zdt.toInstant())
                    .openPrice(price * 0.99)
                    .highPrice(price * 1.01)
                    .lowPrice(price * 0.98)
                    .closePrice(price)
                    .volume(10000.0)
                    .build());

            zdt = zdt.plusDays(1);
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        SMAIndicator sma20 = new SMAIndicator(closePrice, 20);

        for (int i = 20; i < series.getBarCount(); i++) {
            double rsiVal = rsi.getValue(i).doubleValue();
            assertFalse(Double.isNaN(rsiVal), "RSI should not be NaN after warmup at index " + i);
            assertTrue(rsiVal >= 0 && rsiVal <= 100, "RSI should be between 0 and 100, got: " + rsiVal);

            double smaVal = sma20.getValue(i).doubleValue();
            assertFalse(Double.isNaN(smaVal), "SMA20 should not be NaN after warmup at index " + i);
        }
    }

    @Test
    void evalFactors_largeScale_performanceAndCompletion() {
        int stockCount = 100;
        int dateCount = 250;
        int factorCount = 6;
        List<String> factorNames = Arrays.asList("rsi_14", "macd_hist", "volume_ratio", "ema12_ema26", "boll_position", "roc_10");

        java.util.Random random = new java.util.Random(42);
        Map<String, Map<String, Map<String, Double>>> allFactorValues = new LinkedHashMap<>();

        for (int s = 0; s < stockCount; s++) {
            String tsCode = String.format("%06d.SZ", s + 1);
            Map<String, Map<String, Double>> stockData = new LinkedHashMap<>();

            for (int d = 0; d < dateCount; d++) {
                String date = "2024" + String.format("%04d", d + 1);
                Map<String, Double> dayData = new LinkedHashMap<>();
                dayData.put("pct_chg", (random.nextDouble() - 0.48) * 6);
                dayData.put("rsi_14", 20 + random.nextDouble() * 60);
                dayData.put("macd_hist", (random.nextDouble() - 0.5) * 2);
                dayData.put("volume_ratio", 0.5 + random.nextDouble() * 2);
                dayData.put("ema12_ema26", 0.9 + random.nextDouble() * 0.2);
                dayData.put("boll_position", random.nextDouble());
                dayData.put("roc_10", (random.nextDouble() - 0.5) * 0.2);
                stockData.put(date, dayData);
            }
            allFactorValues.put(tsCode, stockData);
        }

        long t0 = System.currentTimeMillis();
        java.util.concurrent.atomic.AtomicInteger factorCompleted = new java.util.concurrent.atomic.AtomicInteger(0);

        List<FactorEvalResult> results = factorEvalService.evalFactors(
                "perf-test", allFactorValues, factorNames, 5, factorCompleted, null);

        long elapsed = System.currentTimeMillis() - t0;

        System.out.println("=== evalFactors 性能测试 ===");
        System.out.println("股票数: " + stockCount);
        System.out.println("日期数: " + dateCount);
        System.out.println("因子数: " + factorCount);
        System.out.println("耗时: " + elapsed + "ms");
        System.out.println("factorCompleted: " + factorCompleted.get());
        System.out.println("结果数: " + results.size());

        for (FactorEvalResult r : results) {
            System.out.printf("  %s: IC=%.4f, ICIR=%.4f, 覆盖率=%.1f%%, IC胜率=%.1f%%%n",
                    r.getFactorLabel(), r.getIcMean(), r.getIcir(),
                    r.getCoverage() * 100, r.getIcWinRate() * 100);
        }

        assertEquals(factorCount, results.size(), "Should have results for all factors");
        assertEquals(factorCount, factorCompleted.get(), "factorCompleted should equal total factors");
        assertTrue(elapsed < 30000, "evalFactors should complete within 30s for 100 stocks, took: " + elapsed + "ms");

        for (FactorEvalResult r : results) {
            assertNotNull(r.getFactorName());
            assertNotNull(r.getIcMean());
            assertTrue(r.getCoverage() >= 0 && r.getCoverage() <= 1);
        }
    }

    @Test
    void evalFactors_fullScale_estimatesCompletionTime() {
        int stockCount = 5000;
        int dateCount = 250;
        List<String> factorNames = Arrays.asList("rsi_14", "macd_hist", "volume_ratio", "ema12_ema26", "boll_position", "roc_10");

        java.util.Random random = new java.util.Random(42);
        Map<String, Map<String, Map<String, Double>>> allFactorValues = new LinkedHashMap<>();

        for (int s = 0; s < stockCount; s++) {
            String tsCode = String.format("%06d.SZ", s + 1);
            Map<String, Map<String, Double>> stockData = new LinkedHashMap<>();

            for (int d = 0; d < dateCount; d++) {
                String date = "2024" + String.format("%04d", d + 1);
                Map<String, Double> dayData = new LinkedHashMap<>();
                dayData.put("pct_chg", (random.nextDouble() - 0.48) * 6);
                dayData.put("rsi_14", 20 + random.nextDouble() * 60);
                dayData.put("macd_hist", (random.nextDouble() - 0.5) * 2);
                dayData.put("volume_ratio", 0.5 + random.nextDouble() * 2);
                dayData.put("ema12_ema26", 0.9 + random.nextDouble() * 0.2);
                dayData.put("boll_position", random.nextDouble());
                dayData.put("roc_10", (random.nextDouble() - 0.5) * 0.2);
                stockData.put(date, dayData);
            }
            allFactorValues.put(tsCode, stockData);
        }

        long t0 = System.currentTimeMillis();
        java.util.concurrent.atomic.AtomicInteger factorCompleted = new java.util.concurrent.atomic.AtomicInteger(0);

        List<FactorEvalResult> results = factorEvalService.evalFactors(
                "fullscale-test", allFactorValues, factorNames, 5, factorCompleted, null);

        long elapsed = System.currentTimeMillis() - t0;

        System.out.println("=== evalFactors 全规模测试 ===");
        System.out.println("股票数: " + stockCount);
        System.out.println("日期数: " + dateCount);
        System.out.println("因子数: " + factorNames.size());
        System.out.println("耗时: " + elapsed + "ms (" + (elapsed / 1000) + "s)");
        System.out.println("factorCompleted: " + factorCompleted.get());
        System.out.println("结果数: " + results.size());

        for (FactorEvalResult r : results) {
            System.out.printf("  %s: IC=%.4f, ICIR=%.4f, 覆盖率=%.1f%%, IC胜率=%.1f%%%n",
                    r.getFactorLabel(), r.getIcMean(), r.getIcir(),
                    r.getCoverage() * 100, r.getIcWinRate() * 100);
        }

        assertEquals(factorNames.size(), results.size());
        assertEquals(factorNames.size(), factorCompleted.get());
    }
}
