package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradingx.model.FactorEvalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.Strategy;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FactorStrategyBuildTest {

    private VisualStrategyBuilder builder;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        builder = new VisualStrategyBuilder();
        objectMapper = new ObjectMapper();
    }

    private FactorEvalResult makeFactor(String name, double icMean) {
        FactorEvalResult f = new FactorEvalResult();
        f.setFactorName(name);
        f.setFactorLabel(name);
        f.setFactorCategory("technical");
        f.setIcMean(icMean);
        f.setIcStd(0.1);
        f.setIcir(icMean / 0.1);
        f.setIcWinRate(0.55);
        f.setCoverage(0.95);
        return f;
    }

    private BarSeries createTestSeries(int barCount) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();
        double price = 100.0;
        ZonedDateTime zdt = ZonedDateTime.of(2024, 1, 2, 15, 0, 0, 0, ZoneId.systemDefault());
        for (int i = 0; i < barCount; i++) {
            price *= (1 + (Math.random() - 0.48) * 0.06);
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(zdt.plusDays(i).toInstant())
                    .openPrice(price * 0.99)
                    .highPrice(price * 1.01)
                    .lowPrice(price * 0.98)
                    .closePrice(price)
                    .volume(1000000.0)
                    .build());
        }
        return series;
    }

    private ObjectNode buildVisualConfig(List<FactorEvalResult> factors) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode indicators = objectMapper.createArrayNode();

        ObjectNode closeInd = objectMapper.createObjectNode();
        closeInd.put("id", "close");
        closeInd.put("type", "ClosePriceIndicator");
        closeInd.set("inputs", objectMapper.createArrayNode());
        closeInd.set("params", objectMapper.createObjectNode());
        indicators.add(closeInd);

        List<String> entryIndicatorIds = new ArrayList<>();
        List<String> exitIndicatorIds = new ArrayList<>();

        for (FactorEvalResult factor : factors) {
            boolean isNegativeIc = factor.getIcMean() < 0;

            switch (factor.getFactorName()) {
                case "rsi_14" -> {
                    addIndicator(indicators, "rsi14", "RSIIndicator", "close", 14);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "rsi14", 30));
                        exitIndicatorIds.add(addOverRule(indicators, "rsi14", 70));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "rsi14", 70));
                        exitIndicatorIds.add(addUnderRule(indicators, "rsi14", 30));
                    }
                }
                case "macd_hist" -> {
                    addIndicator(indicators, "macd", "MACDIndicator", "close", 12, 26);
                    addIndicator(indicators, "macdSignal", "EMAIndicator", "macd", 9);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "macd", "macdSignal"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "macd", "macdSignal"));
                    } else {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "macd", "macdSignal"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "macd", "macdSignal"));
                    }
                }
                case "sma_5", "sma_10", "sma_20", "sma_60" -> {
                    int barCount = Integer.parseInt(factor.getFactorName().split("_")[1]);
                    String smaId = "sma" + barCount;
                    addIndicator(indicators, smaId, "SMAIndicator", "close", barCount);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "close", smaId));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "close", smaId));
                    } else {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "close", smaId));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "close", smaId));
                    }
                }
                case "ema_12", "ema_26" -> {
                    int barCount = Integer.parseInt(factor.getFactorName().split("_")[1]);
                    String emaId = "ema" + barCount;
                    addIndicator(indicators, emaId, "EMAIndicator", "close", barCount);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "close", emaId));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "close", emaId));
                    } else {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "close", emaId));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "close", emaId));
                    }
                }
                case "boll_position" -> {
                    addIndicator(indicators, "sma20b", "SMAIndicator", "close", 20);
                    addIndicator(indicators, "bollMiddle", "BollingerBandsMiddleIndicator", "sma20b");
                    addIndicator(indicators, "std20", "StandardDeviationIndicator", "close", 20);
                    addIndicator(indicators, "bollUpper", "BollingerBandsUpperIndicator", "bollMiddle", 0, "std20");
                    addIndicator(indicators, "bollLower", "BollingerBandsLowerIndicator", "bollMiddle", 0, "std20");
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addOverRule(indicators, "close", "bollUpper"));
                        exitIndicatorIds.add(addUnderRule(indicators, "close", "bollLower"));
                    } else {
                        entryIndicatorIds.add(addUnderRule(indicators, "close", "bollLower"));
                        exitIndicatorIds.add(addOverRule(indicators, "close", "bollUpper"));
                    }
                }
                case "cci_20" -> {
                    addIndicator(indicators, "cci20", "CCIIndicator", 20);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "cci20", -100));
                        exitIndicatorIds.add(addOverRule(indicators, "cci20", 100));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "cci20", 100));
                        exitIndicatorIds.add(addUnderRule(indicators, "cci20", -100));
                    }
                }
                case "williams_r" -> {
                    addIndicator(indicators, "wr14", "WilliamsRIndicator", 14);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "wr14", -80));
                        exitIndicatorIds.add(addOverRule(indicators, "wr14", -20));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "wr14", -20));
                        exitIndicatorIds.add(addUnderRule(indicators, "wr14", -80));
                    }
                }
                case "roc_12" -> {
                    addIndicator(indicators, "roc12", "ROCIndicator", "close", 12);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "roc12", -0.03));
                        exitIndicatorIds.add(addOverRule(indicators, "roc12", 0.03));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "roc12", 0.03));
                        exitIndicatorIds.add(addUnderRule(indicators, "roc12", -0.03));
                    }
                }
                case "turnover" -> {
                    addIndicator(indicators, "volume", "VolumeIndicator");
                    addIndicator(indicators, "volSma5", "SMAIndicator", "volume", 5);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "volume", "volSma5"));
                        exitIndicatorIds.add(addOverRule(indicators, "volume", "volSma5"));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "volume", "volSma5"));
                        exitIndicatorIds.add(addUnderRule(indicators, "volume", "volSma5"));
                    }
                }
                case "volume_ratio" -> {
                    addIndicator(indicators, "volume", "VolumeIndicator");
                    addIndicator(indicators, "volSma5", "SMAIndicator", "volume", 5);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "volume", "volSma5"));
                        exitIndicatorIds.add(addOverRule(indicators, "volume", "volSma5"));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "volume", "volSma5"));
                        exitIndicatorIds.add(addUnderRule(indicators, "volume", "volSma5"));
                    }
                }
                case "kdj_k" -> {
                    addIndicator(indicators, "stochK", "StochasticOscillatorKIndicator", 14);
                    addIndicator(indicators, "stochD", "StochasticOscillatorDIndicator", "stochK");
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "stochK", "stochD"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "stochK", "stochD"));
                    } else {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "stochK", "stochD"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "stochK", "stochD"));
                    }
                }
                case "atr_14" -> {
                    addIndicator(indicators, "atr14", "ATRIndicator", 14);
                    addIndicator(indicators, "atrSma20", "SMAIndicator", "atr14", 20);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "atr14", "atrSma20"));
                        exitIndicatorIds.add(addOverRule(indicators, "atr14", "atrSma20"));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "atr14", "atrSma20"));
                        exitIndicatorIds.add(addUnderRule(indicators, "atr14", "atrSma20"));
                    }
                }
                case "return_rate" -> {
                    addIndicator(indicators, "retRate", "ROCIndicator", "close", 1);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "retRate", -0.03));
                        exitIndicatorIds.add(addOverRule(indicators, "retRate", 0.03));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "retRate", 0.03));
                        exitIndicatorIds.add(addUnderRule(indicators, "retRate", -0.03));
                    }
                }
                case "amplitude" -> {
                    addIndicator(indicators, "ampSma20", "SMAIndicator", "close", 20);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "close", "ampSma20"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "close", "ampSma20"));
                    } else {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "close", "ampSma20"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "close", "ampSma20"));
                    }
                }
                case "gap" -> {
                    addIndicator(indicators, "openPrice", "OpenPriceIndicator");
                    addIndicator(indicators, "prevClose", "PreviousValueIndicator", "close", 1);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "openPrice", "prevClose"));
                        exitIndicatorIds.add(addOverRule(indicators, "openPrice", "prevClose"));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "openPrice", "prevClose"));
                        exitIndicatorIds.add(addUnderRule(indicators, "openPrice", "prevClose"));
                    }
                }
                case "high_low_dist" -> {
                    addIndicator(indicators, "sma20hld", "SMAIndicator", "close", 20);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "close", "sma20hld"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "close", "sma20hld"));
                    } else {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "close", "sma20hld"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "close", "sma20hld"));
                    }
                }
                case "std_20" -> {
                    addIndicator(indicators, "std20", "StandardDeviationIndicator", "close", 20);
                    addIndicator(indicators, "stdSma20", "SMAIndicator", "std20", 20);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "std20", "stdSma20"));
                        exitIndicatorIds.add(addOverRule(indicators, "std20", "stdSma20"));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "std20", "stdSma20"));
                        exitIndicatorIds.add(addUnderRule(indicators, "std20", "stdSma20"));
                    }
                }
                case "obv" -> {
                    addIndicator(indicators, "obv", "OnBalanceVolumeIndicator");
                    addIndicator(indicators, "obvSma20", "SMAIndicator", "obv", 20);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "obv", "obvSma20"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "obv", "obvSma20"));
                    } else {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "obv", "obvSma20"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "obv", "obvSma20"));
                    }
                }
                case "skewness_20" -> {
                    addIndicator(indicators, "roc1sk", "ROCIndicator", "close", 1);
                    addIndicator(indicators, "rocSma20", "SMAIndicator", "roc1sk", 20);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "roc1sk", "rocSma20"));
                        exitIndicatorIds.add(addOverRule(indicators, "roc1sk", "rocSma20"));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "roc1sk", "rocSma20"));
                        exitIndicatorIds.add(addUnderRule(indicators, "roc1sk", "rocSma20"));
                    }
                }
                case "kurtosis_20" -> {
                    addIndicator(indicators, "var20", "VarianceIndicator", "close", 20);
                    addIndicator(indicators, "varSma20", "SMAIndicator", "var20", 20);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "var20", "varSma20"));
                        exitIndicatorIds.add(addOverRule(indicators, "var20", "varSma20"));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "var20", "varSma20"));
                        exitIndicatorIds.add(addUnderRule(indicators, "var20", "varSma20"));
                    }
                }
                default -> {}
            }
        }

        root.set("indicators", indicators);

        ObjectNode entryGroup = objectMapper.createObjectNode();
        entryGroup.put("kind", "group");
        entryGroup.put("combinator", "OR");
        ArrayNode entryChildren = objectMapper.createArrayNode();
        for (String ruleJson : entryIndicatorIds) {
            try { entryChildren.add(objectMapper.readTree(ruleJson)); } catch (Exception e) { throw new RuntimeException(e); }
        }
        entryGroup.set("children", entryChildren);
        root.set("entryRule", entryGroup);

        ObjectNode exitGroup = objectMapper.createObjectNode();
        exitGroup.put("kind", "group");
        exitGroup.put("combinator", "AND");
        ArrayNode exitChildren = objectMapper.createArrayNode();
        for (String ruleJson : exitIndicatorIds) {
            try { exitChildren.add(objectMapper.readTree(ruleJson)); } catch (Exception e) { throw new RuntimeException(e); }
        }
        exitGroup.set("children", exitChildren);
        root.set("exitRule", exitGroup);

        return root;
    }

    private void addIndicator(ArrayNode indicators, String id, String type) {
        ObjectNode ind = objectMapper.createObjectNode();
        ind.put("id", id);
        ind.put("type", type);
        ind.set("inputs", objectMapper.createArrayNode());
        ind.set("params", objectMapper.createObjectNode());
        indicators.add(ind);
    }

    private void addIndicator(ArrayNode indicators, String id, String type, String input, int barCount) {
        ObjectNode ind = objectMapper.createObjectNode();
        ind.put("id", id);
        ind.put("type", type);
        ArrayNode inputs = objectMapper.createArrayNode();
        inputs.add(input);
        ind.set("inputs", inputs);
        ObjectNode params = objectMapper.createObjectNode();
        params.put("barCount", barCount);
        ind.set("params", params);
        indicators.add(ind);
    }

    private void addIndicator(ArrayNode indicators, String id, String type, String input) {
        ObjectNode ind = objectMapper.createObjectNode();
        ind.put("id", id);
        ind.put("type", type);
        ArrayNode inputs = objectMapper.createArrayNode();
        inputs.add(input);
        ind.set("inputs", inputs);
        ind.set("params", objectMapper.createObjectNode());
        indicators.add(ind);
    }

    private void addIndicator(ArrayNode indicators, String id, String type, String input1, int param1, String input2) {
        ObjectNode ind = objectMapper.createObjectNode();
        ind.put("id", id);
        ind.put("type", type);
        ArrayNode inputs = objectMapper.createArrayNode();
        inputs.add(input1);
        inputs.add(input2);
        ind.set("inputs", inputs);
        ObjectNode params = objectMapper.createObjectNode();
        params.put("barCount", param1);
        ind.set("params", params);
        indicators.add(ind);
    }

    private void addIndicator(ArrayNode indicators, String id, String type, String input, int shortBar, int longBar) {
        ObjectNode ind = objectMapper.createObjectNode();
        ind.put("id", id);
        ind.put("type", type);
        ArrayNode inputs = objectMapper.createArrayNode();
        inputs.add(input);
        ind.set("inputs", inputs);
        ObjectNode params = objectMapper.createObjectNode();
        params.put("shortBarCount", shortBar);
        params.put("longBarCount", longBar);
        ind.set("params", params);
        indicators.add(ind);
    }

    private void addIndicator(ArrayNode indicators, String id, String type, int barCount) {
        ObjectNode ind = objectMapper.createObjectNode();
        ind.put("id", id);
        ind.put("type", type);
        ind.set("inputs", objectMapper.createArrayNode());
        ObjectNode params = objectMapper.createObjectNode();
        params.put("barCount", barCount);
        ind.set("params", params);
        indicators.add(ind);
    }

    private String addUnderRule(ArrayNode indicators, String indicatorId, double threshold) {
        String ruleId = "rule_" + indicatorId + "_under_" + Math.abs((int) threshold);
        String thresholdId = "thresh_" + indicatorId + "_" + Math.abs((int) threshold);
        ObjectNode threshInd = objectMapper.createObjectNode();
        threshInd.put("id", thresholdId);
        threshInd.put("type", "ConstantIndicator");
        threshInd.set("inputs", objectMapper.createArrayNode());
        ObjectNode threshParams = objectMapper.createObjectNode();
        threshParams.put("value", threshold);
        threshInd.set("params", threshParams);
        indicators.add(threshInd);
        return buildLeafRuleJson(ruleId, "UnderIndicatorRule", indicatorId, thresholdId);
    }

    private String addOverRule(ArrayNode indicators, String indicatorId, double threshold) {
        String ruleId = "rule_" + indicatorId + "_over_" + Math.abs((int) threshold);
        String thresholdId = "thresh_" + indicatorId + "_" + Math.abs((int) threshold);
        ObjectNode threshInd = objectMapper.createObjectNode();
        threshInd.put("id", thresholdId);
        threshInd.put("type", "ConstantIndicator");
        threshInd.set("inputs", objectMapper.createArrayNode());
        ObjectNode threshParams = objectMapper.createObjectNode();
        threshParams.put("value", threshold);
        threshInd.set("params", threshParams);
        indicators.add(threshInd);
        return buildLeafRuleJson(ruleId, "OverIndicatorRule", indicatorId, thresholdId);
    }

    private String addCrossedUpRule(ArrayNode indicators, String ind1, String ind2) {
        return buildLeafRuleJson("rule_" + ind1 + "_xup_" + ind2, "CrossedUpIndicatorRule", ind1, ind2);
    }

    private String addCrossedDownRule(ArrayNode indicators, String ind1, String ind2) {
        return buildLeafRuleJson("rule_" + ind1 + "_xdown_" + ind2, "CrossedDownIndicatorRule", ind1, ind2);
    }

    private String addUnderRule(ArrayNode indicators, String ind1, String ind2) {
        return buildLeafRuleJson("rule_" + ind1 + "_under_" + ind2, "UnderIndicatorRule", ind1, ind2);
    }

    private String addOverRule(ArrayNode indicators, String ind1, String ind2) {
        return buildLeafRuleJson("rule_" + ind1 + "_over_" + ind2, "OverIndicatorRule", ind1, ind2);
    }

    private String buildLeafRuleJson(String ruleId, String type, String... indicatorInputs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"kind\":\"leaf\",\"type\":\"").append(type).append("\",\"indicatorInputs\":[");
        for (int i = 0; i < indicatorInputs.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(indicatorInputs[i]).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private Strategy buildStrategy(ObjectNode config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            BarSeries series = createTestSeries(100);
            return builder.build(json, series);
        } catch (Exception e) {
            throw new RuntimeException("策略构建失败: " + e.getMessage(), e);
        }
    }

    @Test
    void testBollPositionStrategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("boll_position", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "boll_position 策略构建不应抛出异常");
    }

    @Test
    void testRsi14Strategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("rsi_14", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "rsi_14 策略构建不应抛出异常");
    }

    @Test
    void testMacdHistStrategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("macd_hist", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "macd_hist 策略构建不应抛出异常");
    }

    @Test
    void testSma20Strategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("sma_20", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "sma_20 策略构建不应抛出异常");
    }

    @Test
    void testKdjKStrategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("kdj_k", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "kdj_k 策略构建不应抛出异常");
    }

    @Test
    void testAtr14Strategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("atr_14", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "atr_14 策略构建不应抛出异常");
    }

    @Test
    void testObvStrategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("obv", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "obv 策略构建不应抛出异常");
    }

    @Test
    void testGapStrategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("gap", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "gap 策略构建不应抛出异常");
    }

    @Test
    void testStd20Strategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("std_20", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "std_20 策略构建不应抛出异常");
    }

    @Test
    void testKurtosis20Strategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("kurtosis_20", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "kurtosis_20 策略构建不应抛出异常");
    }

    @Test
    void testTurnoverStrategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("turnover", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "turnover 策略构建不应抛出异常");
    }

    @Test
    void testVolumeRatioStrategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("volume_ratio", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "volume_ratio 策略构建不应抛出异常");
    }

    @Test
    void testNegativeIcStrategy() {
        List<FactorEvalResult> factors = List.of(makeFactor("rsi_14", -0.05));
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "负IC的rsi_14策略构建不应抛出异常");
    }

    @Test
    void testMultiFactorStrategy() {
        List<FactorEvalResult> factors = List.of(
                makeFactor("rsi_14", 0.05),
                makeFactor("macd_hist", 0.03),
                makeFactor("boll_position", 0.04)
        );
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "多因子策略构建不应抛出异常");
    }

    @Test
    void testGapHighLowDistAmplitudeStrategy() {
        List<FactorEvalResult> factors = List.of(
                makeFactor("gap", 0.05),
                makeFactor("high_low_dist", 0.03),
                makeFactor("amplitude", 0.04)
        );
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "gap+high_low_dist+amplitude策略构建不应抛出异常");
    }

    @Test
    void testAllFactorsCombined() {
        List<FactorEvalResult> factors = List.of(
                makeFactor("rsi_14", 0.05),
                makeFactor("macd_hist", 0.03),
                makeFactor("sma_20", 0.04),
                makeFactor("boll_position", 0.06),
                makeFactor("cci_20", 0.02),
                makeFactor("atr_14", 0.03),
                makeFactor("obv", 0.04),
                makeFactor("gap", 0.05),
                makeFactor("std_20", 0.02),
                makeFactor("kurtosis_20", 0.01)
        );
        ObjectNode config = buildVisualConfig(factors);
        assertDoesNotThrow(() -> buildStrategy(config), "所有因子组合策略构建不应抛出异常");
    }

    @Test
    void testGeneratedJsonIsValid() throws Exception {
        List<FactorEvalResult> factors = List.of(makeFactor("boll_position", 0.05));
        ObjectNode config = buildVisualConfig(factors);
        String json = objectMapper.writeValueAsString(config);
        JsonNode parsed = objectMapper.readTree(json);
        assertTrue(parsed.has("indicators"), "JSON应包含indicators");
        assertTrue(parsed.has("entryRule"), "JSON应包含entryRule");
        assertTrue(parsed.has("exitRule"), "JSON应包含exitRule");

        JsonNode indicators = parsed.get("indicators");
        for (JsonNode ind : indicators) {
            assertTrue(ind.has("id"), "每个指标应有id");
            assertTrue(ind.has("type"), "每个指标应有type");
            assertTrue(ind.has("inputs"), "每个指标应有inputs");
            assertTrue(ind.has("params"), "每个指标应有params");
        }
    }
}
