package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradingx.model.FactorCombinationResult;
import com.tradingx.model.FactorEvalResult;
import com.tradingx.model.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FactorStrategyService {

    private static final Logger log = LoggerFactory.getLogger(FactorStrategyService.class);

    private final StrategyService strategyService;
    private final VisualStrategyBuilder visualStrategyBuilder;
    private final FactorCalcService factorCalcService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FactorStrategyService(StrategyService strategyService,
                                  VisualStrategyBuilder visualStrategyBuilder,
                                  FactorCalcService factorCalcService) {
        this.strategyService = strategyService;
        this.visualStrategyBuilder = visualStrategyBuilder;
        this.factorCalcService = factorCalcService;
    }

    private static final Map<String, FactorRuleTemplate> FACTOR_RULE_TEMPLATES = Map.ofEntries(
            Map.entry("rsi_14", new FactorRuleTemplate(
                    "RSIIndicator", "close", 14,
                    "超卖买入", "超买卖出",
                    30.0, 70.0, true
            )),
            Map.entry("macd_hist", new FactorRuleTemplate(
                    "MACDCrossRule", "close", 0,
                    "MACD金叉", "MACD死叉",
                    0.0, 0.0, false
            )),
            Map.entry("kdj_k", new FactorRuleTemplate(
                    "StochasticOscillatorKIndicator", "close", 9,
                    "KDJ超卖", "KDJ超买",
                    20.0, 80.0, true
            )),
            Map.entry("cci_14", new FactorRuleTemplate(
                    "CCIIndicator", "close", 14,
                    "CCI超卖", "CCI超买",
                    -100.0, 100.0, true
            )),
            Map.entry("wr_14", new FactorRuleTemplate(
                    "WilliamsRIndicator", "close", 14,
                    "WR超卖", "WR超买",
                    -80.0, -20.0, true
            )),
            Map.entry("roc_10", new FactorRuleTemplate(
                    "ROCIndicator", "close", 10,
                    "ROC转正", "ROC转负",
                    0.0, 0.0, false
            )),
            Map.entry("sma5_sma20", new FactorRuleTemplate(
                    "SMA5CrossSMA20", "close", 0,
                    "SMA5上穿SMA20", "SMA5下穿SMA20",
                    0.0, 0.0, false
            )),
            Map.entry("sma10_sma30", new FactorRuleTemplate(
                    "SMA10CrossSMA30", "close", 0,
                    "SMA10上穿SMA30", "SMA10下穿SMA30",
                    0.0, 0.0, false
            )),
            Map.entry("ema12_ema26", new FactorRuleTemplate(
                    "EMA12CrossEMA26", "close", 0,
                    "EMA12上穿EMA26", "EMA12下穿EMA26",
                    0.0, 0.0, false
            )),
            Map.entry("boll_position", new FactorRuleTemplate(
                    "BollingerPosition", "close", 20,
                    "触及布林下轨", "触及布林上轨",
                    0.0, 1.0, true
            )),
            Map.entry("atr_14", new FactorRuleTemplate(
                    "ATRIndicator", "close", 14,
                    "ATR收缩", "ATR扩张",
                    0.0, 0.0, false
            )),
            Map.entry("turnover", new FactorRuleTemplate(
                    "TurnoverRule", "close", 0,
                    "换手率放大", "换手率萎缩",
                    0.0, 0.0, false
            )),
            Map.entry("volume_ratio", new FactorRuleTemplate(
                    "VolumeRatioRule", "close", 0,
                    "放量", "缩量",
                    1.5, 0.5, true
            )),
            Map.entry("return_rate", new FactorRuleTemplate(
                    "ReturnRateRule", "close", 0,
                    "超跌反弹", "涨幅过大",
                    -0.03, 0.03, true
            )),
            Map.entry("amplitude", new FactorRuleTemplate(
                    "AmplitudeRule", "close", 0,
                    "振幅收缩", "振幅放大",
                    0.0, 0.0, false
            )),
            Map.entry("gap", new FactorRuleTemplate(
                    "GapRule", "close", 0,
                    "跳空低开", "跳空高开",
                    0.0, 0.0, false
            )),
            Map.entry("high_low_dist", new FactorRuleTemplate(
                    "HighLowDistRule", "close", 0,
                    "接近新低", "接近新高",
                    0.2, 0.8, true
            )),
            Map.entry("std_20", new FactorRuleTemplate(
                    "StdDevRule", "close", 20,
                    "波动收缩", "波动放大",
                    0.0, 0.0, false
            )),
            Map.entry("obv", new FactorRuleTemplate(
                    "OBVRule", "close", 0,
                    "OBV上升", "OBV下降",
                    0.0, 0.0, false
            )),
            Map.entry("skewness_20", new FactorRuleTemplate(
                    "SkewnessRule", "close", 20,
                    "负偏度反转", "正偏度反转",
                    -0.5, 0.5, true
            )),
            Map.entry("kurtosis_20", new FactorRuleTemplate(
                    "KurtosisRule", "close", 20,
                    "低峰度", "高峰度",
                    0.0, 3.0, true
            ))
    );

    public Strategy generateStrategy(List<FactorEvalResult> selectedFactors, String username) {
        List<FactorEvalResult> sorted = selectedFactors.stream()
                .sorted(Comparator.comparingDouble(FactorEvalResult::getIcir).reversed())
                .collect(Collectors.toList());

        String strategyName = buildStrategyName(sorted);
        ObjectNode config = buildVisualConfig(sorted);

        String jsonConfig;
        try {
            jsonConfig = objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new RuntimeException("生成策略配置失败: " + e.getMessage(), e);
        }

        String validationError = visualStrategyBuilder.validate(jsonConfig);
        if (validationError != null) {
            log.warn("因子策略验证失败: {}, 尝试简化策略", validationError);
            ObjectNode simplified = buildSimplifiedConfig(sorted);
            try {
                jsonConfig = objectMapper.writeValueAsString(simplified);
            } catch (Exception e) {
                throw new RuntimeException("生成简化策略配置失败: " + e.getMessage(), e);
            }
            validationError = visualStrategyBuilder.validate(jsonConfig);
            if (validationError != null) {
                throw new RuntimeException("策略验证失败: " + validationError);
            }
        }

        Strategy strategy = new Strategy();
        strategy.setName(strategyName);
        strategy.setLanguage("visual");
        strategy.setCode(jsonConfig);
        strategy.setDescription(buildDescription(sorted));
        strategy.setCreatedBy(username);
        strategy.setCreatedByRole("user");

        return strategyService.create(strategy, null);
    }

    public Strategy generateScoringStrategy(List<FactorEvalResult> selectedFactors,
                                             String taskId,
                                             String username) {
        FactorCombinationResult combination = factorCalcService.getCombinationResult(taskId);
        if (combination == null) {
            throw new RuntimeException("该任务的回归打分数据不可用，请重新运行因子计算后再使用回归打分模式。或切换为「规则组合」模式生成策略。");
        }

        List<FactorEvalResult> sorted = selectedFactors.stream()
                .sorted(Comparator.comparingDouble(FactorEvalResult::getIcir).reversed())
                .collect(Collectors.toList());

        String strategyName = buildScoringStrategyName(sorted);
        ObjectNode config = buildScoringVisualConfig(sorted, combination);

        String jsonConfig;
        try {
            jsonConfig = objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new RuntimeException("生成打分策略配置失败: " + e.getMessage(), e);
        }

        String validationError = visualStrategyBuilder.validate(jsonConfig);
        if (validationError != null) {
            throw new RuntimeException("打分策略验证失败: " + validationError);
        }

        Strategy strategy = new Strategy();
        strategy.setName(strategyName);
        strategy.setLanguage("visual");
        strategy.setCode(jsonConfig);
        strategy.setDescription(buildScoringDescription(sorted, combination));
        strategy.setCreatedBy(username);
        strategy.setCreatedByRole("user");

        return strategyService.create(strategy, null);
    }

    private String buildScoringStrategyName(List<FactorEvalResult> factors) {
        StringBuilder sb = new StringBuilder("FS_");
        int limit = Math.min(factors.size(), 3);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append("_");
            sb.append(factors.get(i).getFactorName());
        }
        if (factors.size() > 3) {
            sb.append("_etc");
        }
        return sb.toString();
    }

    private String buildScoringDescription(List<FactorEvalResult> factors, FactorCombinationResult combination) {
        StringBuilder sb = new StringBuilder("Fama-MacBeth回归打分策略\n");
        sb.append(String.format("R²=%.4f, 回归期数=%d, 平均样本量=%d\n",
                combination.getRSquared(), combination.getSamplePeriods(), combination.getAvgSampleSize()));
        sb.append("因子权重:\n");
        for (FactorEvalResult f : factors) {
            Double weight = combination.getWeights().get(f.getFactorName());
            Double tStat = combination.getTStats().get(f.getFactorName());
            Double pValue = combination.getPValues().get(f.getFactorName());
            sb.append(String.format("- %s: w=%.4f, t=%.2f, p=%.4f\n",
                    f.getFactorLabel(),
                    weight != null ? weight : 0,
                    tStat != null ? tStat : 0,
                    pValue != null ? pValue : 1));
        }
        sb.append(String.format("\n入场阈值: +%.1f, 出场阈值: %.1f",
                combination.getEntryThreshold(), combination.getExitThreshold()));
        return sb.toString();
    }

    private ObjectNode buildScoringVisualConfig(List<FactorEvalResult> factors,
                                                  FactorCombinationResult combination) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode indicators = objectMapper.createArrayNode();

        ObjectNode closeInd = objectMapper.createObjectNode();
        closeInd.put("id", "close");
        closeInd.put("type", "ClosePriceIndicator");
        closeInd.set("inputs", objectMapper.createArrayNode());
        closeInd.set("params", objectMapper.createObjectNode());
        indicators.add(closeInd);

        ObjectNode volumeInd = objectMapper.createObjectNode();
        volumeInd.put("id", "volume");
        volumeInd.put("type", "VolumeIndicator");
        volumeInd.set("inputs", objectMapper.createArrayNode());
        volumeInd.set("params", objectMapper.createObjectNode());
        indicators.add(volumeInd);

        List<String> factorIndicatorIds = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        for (FactorEvalResult factor : factors) {
            String finalId = addFactorIndicators(indicators, factor.getFactorName());
            if (finalId != null) {
                factorIndicatorIds.add(finalId);
                Double w = combination.getWeights().get(factor.getFactorName());
                weights.add(w != null ? w : 0);
            }
        }

        if (factorIndicatorIds.isEmpty()) {
            throw new RuntimeException("所选因子无法生成有效打分策略");
        }

        ObjectNode compositeInd = objectMapper.createObjectNode();
        compositeInd.put("id", "composite");
        compositeInd.put("type", "WeightedCompositeIndicator");
        ArrayNode compInputs = objectMapper.createArrayNode();
        for (String id : factorIndicatorIds) {
            compInputs.add(id);
        }
        compositeInd.set("inputs", compInputs);
        ObjectNode compParams = objectMapper.createObjectNode();
        ArrayNode weightsArr = objectMapper.createArrayNode();
        for (Double w : weights) {
            weightsArr.add(w);
        }
        compParams.set("weights", weightsArr);
        compParams.put("lookbackPeriod", 120);
        compositeInd.set("params", compParams);
        indicators.add(compositeInd);

        ObjectNode entryThreshInd = objectMapper.createObjectNode();
        entryThreshInd.put("id", "entryThreshold");
        entryThreshInd.put("type", "ConstantIndicator");
        entryThreshInd.set("inputs", objectMapper.createArrayNode());
        ObjectNode entryThreshParams = objectMapper.createObjectNode();
        entryThreshParams.put("value", combination.getEntryThreshold());
        entryThreshInd.set("params", entryThreshParams);
        indicators.add(entryThreshInd);

        ObjectNode exitThreshInd = objectMapper.createObjectNode();
        exitThreshInd.put("id", "exitThreshold");
        exitThreshInd.put("type", "ConstantIndicator");
        exitThreshInd.set("inputs", objectMapper.createArrayNode());
        ObjectNode exitThreshParams = objectMapper.createObjectNode();
        exitThreshParams.put("value", combination.getExitThreshold());
        exitThreshInd.set("params", exitThreshParams);
        indicators.add(exitThreshInd);

        root.set("indicators", indicators);

        ObjectNode entryRule = objectMapper.createObjectNode();
        entryRule.put("kind", "leaf");
        entryRule.put("type", "OverIndicatorRule");
        ArrayNode entryInputs = objectMapper.createArrayNode();
        entryInputs.add("composite");
        entryInputs.add("entryThreshold");
        entryRule.set("indicatorInputs", entryInputs);
        root.set("entryRule", entryRule);

        ObjectNode exitRule = objectMapper.createObjectNode();
        exitRule.put("kind", "leaf");
        exitRule.put("type", "UnderIndicatorRule");
        ArrayNode exitInputs = objectMapper.createArrayNode();
        exitInputs.add("composite");
        exitInputs.add("exitThreshold");
        exitRule.set("indicatorInputs", exitInputs);

        ObjectNode stopLossRule = objectMapper.createObjectNode();
        stopLossRule.put("kind", "leaf");
        stopLossRule.put("type", "StopLossRule");
        ArrayNode slInputs = objectMapper.createArrayNode();
        slInputs.add("close");
        stopLossRule.set("indicatorInputs", slInputs);
        ObjectNode slParams = objectMapper.createObjectNode();
        slParams.put("lossPercentage", 5.0);
        stopLossRule.set("params", slParams);

        ObjectNode trailingStopRule = objectMapper.createObjectNode();
        trailingStopRule.put("kind", "leaf");
        trailingStopRule.put("type", "TrailingStopLossRule");
        ArrayNode tslInputs = objectMapper.createArrayNode();
        tslInputs.add("close");
        trailingStopRule.set("indicatorInputs", tslInputs);
        ObjectNode tslParams = objectMapper.createObjectNode();
        tslParams.put("lossPercentage", 10.0);
        trailingStopRule.set("params", tslParams);

        ObjectNode exitOrGroup = objectMapper.createObjectNode();
        exitOrGroup.put("kind", "group");
        exitOrGroup.put("combinator", "OR");
        ArrayNode exitChildren = objectMapper.createArrayNode();
        exitChildren.add(exitRule);
        exitChildren.add(stopLossRule);
        exitChildren.add(trailingStopRule);
        exitOrGroup.set("children", exitChildren);

        root.set("exitRule", exitOrGroup);

        return root;
    }

    private String addFactorIndicators(ArrayNode indicators, String factorName) {
        return switch (factorName) {
            case "rsi_14" -> {
                addIndicator(indicators, "rsi14", "RSIIndicator", "close", 14);
                yield "rsi14";
            }
            case "macd_hist" -> {
                addIndicator(indicators, "macd", "MACDIndicator", "close", 12);
                addIndicator(indicators, "macdsignal", "EMAIndicator", "macd", 9);
                addIndicator(indicators, "macdhist", "DifferenceIndicator", "macd", "macdsignal");
                yield "macdhist";
            }
            case "kdj_k" -> {
                addIndicator(indicators, "stochK", "StochasticOscillatorKIndicator", "close", 9);
                yield "stochK";
            }
            case "cci_14" -> {
                addIndicator(indicators, "cci14", "CCIIndicator", "close", 14);
                yield "cci14";
            }
            case "wr_14" -> {
                addIndicator(indicators, "wr14", "WilliamsRIndicator", "close", 14);
                yield "wr14";
            }
            case "roc_10" -> {
                addIndicator(indicators, "roc10", "ROCIndicator", "close", 10);
                yield "roc10";
            }
            case "sma5_sma20" -> {
                addIndicator(indicators, "sma5", "SMAIndicator", "close", 5);
                addIndicator(indicators, "sma20", "SMAIndicator", "close", 20);
                addIndicator(indicators, "sma5sma20", "CombineIndicatorDivide", "sma5", "sma20");
                yield "sma5sma20";
            }
            case "sma10_sma30" -> {
                addIndicator(indicators, "sma10", "SMAIndicator", "close", 10);
                addIndicator(indicators, "sma30", "SMAIndicator", "close", 30);
                addIndicator(indicators, "sma10sma30", "CombineIndicatorDivide", "sma10", "sma30");
                yield "sma10sma30";
            }
            case "ema12_ema26" -> {
                addIndicator(indicators, "ema12", "EMAIndicator", "close", 12);
                addIndicator(indicators, "ema26", "EMAIndicator", "close", 26);
                addIndicator(indicators, "ema12ema26", "CombineIndicatorDivide", "ema12", "ema26");
                yield "ema12ema26";
            }
            case "boll_position" -> {
                addIndicator(indicators, "sma20b", "SMAIndicator", "close", 20);
                addIndicator(indicators, "bollMiddle", "BollingerBandsMiddleIndicator", "sma20b");
                addIndicator(indicators, "std20b", "StandardDeviationIndicator", "close", 20);
                addIndicator(indicators, "bollUpper", "BollingerBandsUpperIndicator", "bollMiddle", 0, "std20b");
                addIndicator(indicators, "bollLower", "BollingerBandsLowerIndicator", "bollMiddle", 0, "std20b");
                addIndicator(indicators, "bollRange", "DifferenceIndicator", "bollUpper", "bollLower");
                addIndicator(indicators, "bollPosRaw", "DifferenceIndicator", "close", "bollLower");
                addIndicator(indicators, "bollPosition", "CombineIndicatorDivide", "bollPosRaw", "bollRange");
                yield "bollPosition";
            }
            case "atr_14" -> {
                addIndicator(indicators, "atr14", "ATRIndicator", "close", 14);
                yield "atr14";
            }
            case "turnover", "volume_ratio" -> {
                addIndicator(indicators, "volSma5", "SMAIndicator", "volume", 5);
                addIndicator(indicators, "volRatio", "CombineIndicatorDivide", "volume", "volSma5");
                yield "volRatio";
            }
            case "return_rate" -> {
                addIndicator(indicators, "retRate", "ROCIndicator", "close", 1);
                yield "retRate";
            }
            case "std_20" -> {
                addIndicator(indicators, "std20", "StandardDeviationIndicator", "close", 20);
                yield "std20";
            }
            case "obv" -> {
                addIndicator(indicators, "obv", "OnBalanceVolumeIndicator");
                addIndicator(indicators, "obvSma20", "SMAIndicator", "obv", 20);
                addIndicator(indicators, "obvRatio", "CombineIndicatorDivide", "obv", "obvSma20");
                yield "obvRatio";
            }
            default -> {
                log.info("因子 {} 暂无打分策略指标定义，跳过", factorName);
                yield null;
            }
        };
    }

    private String buildStrategyName(List<FactorEvalResult> factors) {
        StringBuilder sb = new StringBuilder("F_");
        int limit = Math.min(factors.size(), 3);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append("_");
            sb.append(factors.get(i).getFactorName());
        }
        if (factors.size() > 3) {
            sb.append("_etc");
        }
        return sb.toString();
    }

    private String buildDescription(List<FactorEvalResult> factors) {
        StringBuilder sb = new StringBuilder("因子挖掘生成策略，基于以下因子：");
        for (FactorEvalResult f : factors) {
            sb.append("\n- ").append(f.getFactorLabel())
              .append(" (ICIR=").append(String.format("%.4f", f.getIcir()))
              .append(", IC均值=").append(String.format("%.4f", f.getIcMean())).append(")");
        }
        return sb.toString();
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
            FactorRuleTemplate template = FACTOR_RULE_TEMPLATES.get(factor.getFactorName());
            if (template == null) continue;

            boolean isNegativeIc = factor.getIcMean() < 0;
            String factorId = factor.getFactorName().replace("_", "");

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
                    addIndicator(indicators, "macd", "MACDIndicator", "close", 12);
                    addIndicator(indicators, "macdsignal", "EMAIndicator", "macd", 9);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "macd", "macdsignal"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "macd", "macdsignal"));
                    } else {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "macd", "macdsignal"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "macd", "macdsignal"));
                    }
                }
                case "kdj_k" -> {
                    addIndicator(indicators, "stochK", "StochasticOscillatorKIndicator", "close", 9);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "stochK", 20));
                        exitIndicatorIds.add(addOverRule(indicators, "stochK", 80));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "stochK", 80));
                        exitIndicatorIds.add(addUnderRule(indicators, "stochK", 20));
                    }
                }
                case "cci_14" -> {
                    addIndicator(indicators, "cci14", "CCIIndicator", "close", 14);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "cci14", -100));
                        exitIndicatorIds.add(addOverRule(indicators, "cci14", 100));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "cci14", 100));
                        exitIndicatorIds.add(addUnderRule(indicators, "cci14", -100));
                    }
                }
                case "wr_14" -> {
                    addIndicator(indicators, "wr14", "WilliamsRIndicator", "close", 14);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addUnderRule(indicators, "wr14", -80));
                        exitIndicatorIds.add(addOverRule(indicators, "wr14", -20));
                    } else {
                        entryIndicatorIds.add(addOverRule(indicators, "wr14", -20));
                        exitIndicatorIds.add(addUnderRule(indicators, "wr14", -80));
                    }
                }
                case "roc_10" -> {
                    addIndicator(indicators, "roc10", "ROCIndicator", "close", 10);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "roc10", "zero"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "roc10", "zero"));
                    } else {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "roc10", "zero"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "roc10", "zero"));
                    }
                }
                case "sma5_sma20" -> {
                    addIndicator(indicators, "sma5", "SMAIndicator", "close", 5);
                    addIndicator(indicators, "sma20", "SMAIndicator", "close", 20);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "sma5", "sma20"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "sma5", "sma20"));
                    } else {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "sma5", "sma20"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "sma5", "sma20"));
                    }
                }
                case "sma10_sma30" -> {
                    addIndicator(indicators, "sma10", "SMAIndicator", "close", 10);
                    addIndicator(indicators, "sma30", "SMAIndicator", "close", 30);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "sma10", "sma30"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "sma10", "sma30"));
                    } else {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "sma10", "sma30"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "sma10", "sma30"));
                    }
                }
                case "ema12_ema26" -> {
                    addIndicator(indicators, "ema12", "EMAIndicator", "close", 12);
                    addIndicator(indicators, "ema26", "EMAIndicator", "close", 26);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "ema12", "ema26"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "ema12", "ema26"));
                    } else {
                        entryIndicatorIds.add(addCrossedDownRule(indicators, "ema12", "ema26"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "ema12", "ema26"));
                    }
                }
                case "boll_position" -> {
                    addIndicator(indicators, "sma20b", "SMAIndicator", "close", 20);
                    addIndicator(indicators, "bollMiddle", "BollingerBandsMiddleIndicator", "sma20b");
                    addIndicator(indicators, "std20", "StandardDeviationIndicator", "close", 20);
                    addIndicator(indicators, "bollUpper", "BollingerBandsUpperIndicator", "bollMiddle", 0, "std20");
                    addIndicator(indicators, "bollLower", "BollingerBandsLowerIndicator", "bollMiddle", 0, "std20");
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "close", "bollLower"));
                        exitIndicatorIds.add(addCrossedUpRule(indicators, "close", "bollUpper"));
                    } else {
                        entryIndicatorIds.add(addCrossedUpRule(indicators, "close", "bollUpper"));
                        exitIndicatorIds.add(addCrossedDownRule(indicators, "close", "bollLower"));
                    }
                }
                case "turnover" -> {
                    addIndicator(indicators, "volSma5", "SMAIndicator", "volume", 5);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addOverRule(indicators, "volume", "volSma5"));
                        exitIndicatorIds.add(addUnderRule(indicators, "volume", "volSma5"));
                    } else {
                        entryIndicatorIds.add(addUnderRule(indicators, "volume", "volSma5"));
                        exitIndicatorIds.add(addOverRule(indicators, "volume", "volSma5"));
                    }
                }
                case "volume_ratio" -> {
                    addIndicator(indicators, "volume", "VolumeIndicator");
                    addIndicator(indicators, "volSma5b", "SMAIndicator", "volume", 5);
                    if (isNegativeIc) {
                        entryIndicatorIds.add(addOverRule(indicators, "volume", "volSma5b"));
                        exitIndicatorIds.add(addUnderRule(indicators, "volume", "volSma5b"));
                    } else {
                        entryIndicatorIds.add(addUnderRule(indicators, "volume", "volSma5b"));
                        exitIndicatorIds.add(addOverRule(indicators, "volume", "volSma5b"));
                    }
                }
                case "atr_14" -> {
                    addIndicator(indicators, "atr14", "ATRIndicator", "close", 14);
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
                default -> log.info("因子 {} 暂无自动策略模板，跳过", factor.getFactorName());
            }
        }

        if (entryIndicatorIds.isEmpty() || exitIndicatorIds.isEmpty()) {
            throw new RuntimeException("所选因子无法生成有效策略，请选择有策略模板的因子（如RSI、MACD、均线等）");
        }

        root.set("indicators", indicators);

        ObjectNode zeroInd = objectMapper.createObjectNode();
        zeroInd.put("id", "zero");
        zeroInd.put("type", "ConstantIndicator");
        zeroInd.set("inputs", objectMapper.createArrayNode());
        ObjectNode zeroParams = objectMapper.createObjectNode();
        zeroParams.put("value", 0.0);
        zeroInd.set("params", zeroParams);
        indicators.add(zeroInd);

        ObjectNode volumeInd = objectMapper.createObjectNode();
        volumeInd.put("id", "volume");
        volumeInd.put("type", "VolumeIndicator");
        volumeInd.set("inputs", objectMapper.createArrayNode());
        volumeInd.set("params", objectMapper.createObjectNode());
        indicators.add(volumeInd);

        root.set("entryRule", buildRuleGroup(entryIndicatorIds, "OR"));

        ObjectNode signalExitGroup = buildRuleGroup(exitIndicatorIds, "AND");

        ObjectNode stopLossRule = objectMapper.createObjectNode();
        stopLossRule.put("kind", "leaf");
        stopLossRule.put("type", "StopLossRule");
        ArrayNode slInputs = objectMapper.createArrayNode();
        slInputs.add("close");
        stopLossRule.set("indicatorInputs", slInputs);
        ObjectNode slParams = objectMapper.createObjectNode();
        slParams.put("lossPercentage", 5.0);
        stopLossRule.set("params", slParams);

        ObjectNode trailingStopRule = objectMapper.createObjectNode();
        trailingStopRule.put("kind", "leaf");
        trailingStopRule.put("type", "TrailingStopLossRule");
        ArrayNode tslInputs = objectMapper.createArrayNode();
        tslInputs.add("close");
        trailingStopRule.set("indicatorInputs", tslInputs);
        ObjectNode tslParams = objectMapper.createObjectNode();
        tslParams.put("lossPercentage", 10.0);
        trailingStopRule.set("params", tslParams);

        ObjectNode exitOrGroup = objectMapper.createObjectNode();
        exitOrGroup.put("kind", "group");
        exitOrGroup.put("combinator", "OR");
        ArrayNode exitChildren = objectMapper.createArrayNode();
        exitChildren.add(signalExitGroup);
        exitChildren.add(stopLossRule);
        exitChildren.add(trailingStopRule);
        exitOrGroup.set("children", exitChildren);

        root.set("exitRule", exitOrGroup);

        return root;
    }

    private ObjectNode buildSimplifiedConfig(List<FactorEvalResult> factors) {
        List<FactorEvalResult> supported = factors.stream()
                .filter(f -> FACTOR_RULE_TEMPLATES.containsKey(f.getFactorName()))
                .limit(3)
                .collect(Collectors.toList());

        if (supported.isEmpty()) {
            throw new RuntimeException("所选因子均无策略模板");
        }

        return buildVisualConfig(supported);
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

    private void addIndicator(ArrayNode indicators, String id, String type, String input1, int barCount, String input2) {
        ObjectNode ind = objectMapper.createObjectNode();
        ind.put("id", id);
        ind.put("type", type);
        ArrayNode inputs = objectMapper.createArrayNode();
        inputs.add(input1);
        inputs.add(input2);
        ind.set("inputs", inputs);
        ObjectNode params = objectMapper.createObjectNode();
        if (barCount > 0) params.put("barCount", barCount);
        ind.set("params", params);
        indicators.add(ind);
    }

    private void addIndicator(ArrayNode indicators, String id, String type) {
        ObjectNode ind = objectMapper.createObjectNode();
        ind.put("id", id);
        ind.put("type", type);
        ind.set("inputs", objectMapper.createArrayNode());
        ind.set("params", objectMapper.createObjectNode());
        indicators.add(ind);
    }

    private void addIndicator(ArrayNode indicators, String id, String type, String input1, String input2) {
        ObjectNode ind = objectMapper.createObjectNode();
        ind.put("id", id);
        ind.put("type", type);
        ArrayNode inputs = objectMapper.createArrayNode();
        inputs.add(input1);
        inputs.add(input2);
        ind.set("inputs", inputs);
        ind.set("params", objectMapper.createObjectNode());
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
        String ruleId = "rule_" + ind1 + "_xup_" + ind2;
        return buildLeafRuleJson(ruleId, "CrossedUpIndicatorRule", ind1, ind2);
    }

    private String addCrossedDownRule(ArrayNode indicators, String ind1, String ind2) {
        String ruleId = "rule_" + ind1 + "_xdown_" + ind2;
        return buildLeafRuleJson(ruleId, "CrossedDownIndicatorRule", ind1, ind2);
    }

    private String addUnderRule(ArrayNode indicators, String ind1, String ind2) {
        String ruleId = "rule_" + ind1 + "_under_" + ind2;
        return buildLeafRuleJson(ruleId, "UnderIndicatorRule", ind1, ind2);
    }

    private String addOverRule(ArrayNode indicators, String ind1, String ind2) {
        String ruleId = "rule_" + ind1 + "_over_" + ind2;
        return buildLeafRuleJson(ruleId, "OverIndicatorRule", ind1, ind2);
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

    private ObjectNode buildRuleGroup(List<String> leafRules, String combinator) {
        ObjectNode group = objectMapper.createObjectNode();
        group.put("kind", "group");
        group.put("combinator", combinator);
        try {
            ArrayNode children = objectMapper.createArrayNode();
            for (String ruleJson : leafRules) {
                JsonNode ruleNode = objectMapper.readTree(ruleJson);
                children.add(ruleNode);
            }
            group.set("children", children);
        } catch (Exception e) {
            log.error("构建规则组失败", e);
        }
        return group;
    }

    private static class FactorRuleTemplate {
        final String indicatorType;
        final String inputIndicator;
        final int barCount;
        final String entryDesc;
        final String exitDesc;
        final double entryThreshold;
        final double exitThreshold;
        final boolean useThreshold;

        FactorRuleTemplate(String indicatorType, String inputIndicator, int barCount,
                           String entryDesc, String exitDesc,
                           double entryThreshold, double exitThreshold, boolean useThreshold) {
            this.indicatorType = indicatorType;
            this.inputIndicator = inputIndicator;
            this.barCount = barCount;
            this.entryDesc = entryDesc;
            this.exitDesc = exitDesc;
            this.entryThreshold = entryThreshold;
            this.exitThreshold = exitThreshold;
            this.useThreshold = useThreshold;
        }
    }
}
