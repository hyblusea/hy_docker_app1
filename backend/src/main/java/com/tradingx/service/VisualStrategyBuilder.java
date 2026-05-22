package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.ChopIndicator;
import org.ta4j.core.indicators.DPOIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.PPOIndicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.StochasticOscillatorDIndicator;
import org.ta4j.core.indicators.WilliamsRIndicator;
import org.ta4j.core.indicators.ParabolicSarIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.indicators.averages.LWMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.candles.BullishEngulfingIndicator;
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator;
import org.ta4j.core.indicators.candles.HammerIndicator;
import org.ta4j.core.indicators.candles.DojiIndicator;
import org.ta4j.core.indicators.candles.MorningStarIndicator;
import org.ta4j.core.indicators.candles.EveningStarIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.CombineIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.SumIndicator;
import org.ta4j.core.indicators.helpers.TRIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.statistics.VarianceIndicator;
import org.ta4j.core.indicators.statistics.CovarianceIndicator;
import org.ta4j.core.indicators.statistics.CorrelationCoefficientIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.indicators.volume.ChaikinMoneyFlowIndicator;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.indicators.volume.AccumulationDistributionIndicator;
import org.ta4j.core.indicators.volume.NVIIndicator;
import org.ta4j.core.indicators.volume.PVIIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.BooleanIndicatorRule;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.InPipeRule;
import org.ta4j.core.rules.IsFallingRule;
import org.ta4j.core.rules.IsHighestRule;
import org.ta4j.core.rules.IsLowestRule;
import org.ta4j.core.rules.IsRisingRule;
import org.ta4j.core.rules.NotRule;
import org.ta4j.core.rules.OrRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.StopGainRule;
import org.ta4j.core.rules.StopLossRule;
import org.ta4j.core.rules.TrailingStopLossRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import com.tradingx.indicators.WeightedCompositeIndicator;
import com.tradingx.rules.MaxTradeBarCountRule;

import java.util.*;

@Component
public class VisualStrategyBuilder {

    private static final Logger log = LoggerFactory.getLogger(VisualStrategyBuilder.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public CompiledStrategy parse(String jsonConfig) {
        try {
            JsonNode root = MAPPER.readTree(jsonConfig);
            return series -> doBuild(root, series);
        } catch (Exception e) {
            log.error("可视化策略JSON解析失败", e);
            throw new RuntimeException("可视化策略JSON解析失败: " + e.getMessage(), e);
        }
    }

    public Strategy build(String jsonConfig, BarSeries series) {
        return parse(jsonConfig).create(series);
    }

    private Strategy doBuild(JsonNode root, BarSeries series) {
        try {
            Map<String, Indicator<Num>> indicatorMap = new LinkedHashMap<>();
            Map<String, Indicator<Boolean>> booleanIndicatorMap = new LinkedHashMap<>();
            buildIndicators(root.get("indicators"), series, indicatorMap, booleanIndicatorMap);

            Rule entryRule;
            Rule exitRule;

            boolean usesNewFormat = root.has("entryRule") && !root.get("entryRule").isNull();
            boolean usesOldFormat = root.has("entryConditions");
            log.info("策略格式检测: newFormat={}, oldFormat={}", usesNewFormat, usesOldFormat);

            if (usesNewFormat) {
                entryRule = buildRuleFromNode(root.get("entryRule"), indicatorMap, booleanIndicatorMap, series);
            } else if (usesOldFormat) {
                entryRule = buildRulesFromConditions(root.get("entryConditions"),
                        root.has("entryCombinator") ? root.get("entryCombinator").asText("AND") : "AND",
                        indicatorMap, booleanIndicatorMap, series);
            } else {
                log.warn("未检测到入场规则定义(entryRule/entryConditions), 策略将不会产生交易");
                entryRule = org.ta4j.core.rules.BooleanRule.FALSE;
            }

            if (root.has("exitRule") && !root.get("exitRule").isNull()) {
                exitRule = buildRuleFromNode(root.get("exitRule"), indicatorMap, booleanIndicatorMap, series);
            } else if (root.has("exitConditions")) {
                exitRule = buildRulesFromConditions(root.get("exitConditions"),
                        root.has("exitCombinator") ? root.get("exitCombinator").asText("OR") : "OR",
                        indicatorMap, booleanIndicatorMap, series);
            } else {
                log.warn("未检测到出场规则定义(exitRule/exitConditions), 策略将不会产生交易");
                exitRule = org.ta4j.core.rules.BooleanRule.FALSE;
            }

            int unstablePeriod = calcUnstablePeriod(root.get("indicators"));
            log.info("可视化策略构建完成: indicators={}, unstablePeriod={}, entryRule={}, exitRule={}",
                    indicatorMap.size() + booleanIndicatorMap.size(), unstablePeriod,
                    entryRule.getClass().getSimpleName(), exitRule.getClass().getSimpleName());

            return new BaseStrategy(entryRule, exitRule, unstablePeriod);
        } catch (Exception e) {
            log.error("可视化策略构建失败", e);
            throw new RuntimeException("可视化策略构建失败: " + e.getMessage(), e);
        }
    }

    private int calcUnstablePeriod(JsonNode indicatorsNode) {
        int maxPeriod = 0;
        if (indicatorsNode != null && indicatorsNode.isArray()) {
            for (JsonNode ind : indicatorsNode) {
                String type = ind.get("type").asText();
                int period = getIndicatorPeriod(type, ind);
                if (period > maxPeriod) {
                    maxPeriod = period;
                }
            }
        }
        return maxPeriod;
    }

    private int getIndicatorPeriod(String type, JsonNode ind) {
        return switch (type) {
            case "SMAIndicator", "EMAIndicator", "WMAIndicator", "LWMAIndicator" -> param(ind, "barCount", 20);
            case "RSIIndicator" -> param(ind, "barCount", 14);
            case "MACDIndicator" -> param(ind, "longBarCount", 26);
            case "ROCIndicator" -> param(ind, "barCount", 12);
            case "PPOIndicator" -> param(ind, "longBarCount", 26);
            case "CCIIndicator" -> param(ind, "barCount", 20);
            case "ATRIndicator" -> param(ind, "barCount", 14);
            case "WilliamsRIndicator" -> param(ind, "barCount", 14);
            case "DPOIndicator" -> param(ind, "barCount", 20);
            case "ChopIndicator" -> param(ind, "timeFrame", 14);
            case "StochasticOscillatorKIndicator" -> param(ind, "barCount", 14);
            case "StochasticOscillatorDIndicator" -> 14;
            case "IchimokuTenkanSenIndicator" -> param(ind, "barCount", 9);
            case "IchimokuKijunSenIndicator" -> param(ind, "barCount", 26);
            case "IchimokuSenkouSpanAIndicator" -> param(ind, "barCountKijun", 26);
            case "IchimokuSenkouSpanBIndicator" -> param(ind, "barCount", 52);
            case "IchimokuChikouSpanIndicator" -> param(ind, "barCount", 26);
            case "StandardDeviationIndicator", "VarianceIndicator" -> param(ind, "barCount", 20);
            case "CovarianceIndicator", "CorrelationCoefficientIndicator" -> param(ind, "barCount", 20);
            case "SimpleLinearRegressionIndicator" -> param(ind, "barCount", 20);
            case "PreviousValueIndicator" -> param(ind, "n", 1);
            case "BollingerBandsMiddleIndicator" -> 20;
            case "BollingerBandsUpperIndicator", "BollingerBandsLowerIndicator" -> 20;
            case "ChaikinMoneyFlowIndicator" -> param(ind, "barCount", 20);
            case "MoneyFlowIndexIndicator" -> param(ind, "barCount", 14);
            case "DojiIndicator" -> param(ind, "barCount", 10);
            case "WeightedCompositeIndicator" -> param(ind, "lookbackPeriod", 120);
            default -> 0;
        };
    }

    public String validate(String jsonConfig) {
        if (jsonConfig == null || jsonConfig.isBlank()) {
            return "策略配置为空";
        }
        try {
            JsonNode root = MAPPER.readTree(jsonConfig);
            if (!root.has("indicators")) return "缺少指标定义";

            boolean hasNewFormat = root.has("entryRule") && root.has("exitRule");
            boolean hasOldFormat = root.has("entryConditions") && root.has("exitConditions");

            if (!hasNewFormat && !hasOldFormat) return "缺少规则定义";

            JsonNode indicators = root.get("indicators");
            Set<String> indicatorIds = new HashSet<>();
            for (JsonNode ind : indicators) {
                String id = ind.get("id").asText();
                indicatorIds.add(id);
            }

            for (JsonNode ind : indicators) {
                String type = ind.get("type").asText();
                if (!SUPPORTED_INDICATORS.contains(type) && !SUPPORTED_BOOLEAN_INDICATORS.contains(type)) {
                    return "不支持的指标类型: " + type;
                }
                JsonNode inputs = ind.get("inputs");
                if (inputs != null) {
                    for (JsonNode input : inputs) {
                        if (!indicatorIds.contains(input.asText())) {
                            return "指标 " + id(ind) + " 引用了不存在的输入指标: " + input.asText();
                        }
                    }
                }
            }

            if (hasNewFormat) {
                String entryErr = validateRuleNode(root.get("entryRule"), indicatorIds);
                if (entryErr != null) return "入场规则: " + entryErr;
                String exitErr = validateRuleNode(root.get("exitRule"), indicatorIds);
                if (exitErr != null) return "出场规则: " + exitErr;
            } else {
                String[] ruleSections = {"entryConditions", "exitConditions"};
                for (String section : ruleSections) {
                    JsonNode conditions = root.get(section);
                    for (JsonNode cond : conditions) {
                        String type = cond.get("type").asText();
                        if (!SUPPORTED_RULES.contains(type)) {
                            return "不支持的规则类型: " + type;
                        }
                        JsonNode indInputs = cond.get("indicatorInputs");
                        if (indInputs != null) {
                            for (JsonNode input : indInputs) {
                                if (!indicatorIds.contains(input.asText())) {
                                    return "规则引用了不存在的指标: " + input.asText();
                                }
                            }
                        }
                    }
                }
            }

            return null;
        } catch (Exception e) {
            return "JSON格式错误: " + e.getMessage();
        }
    }

    private String validateRuleNode(JsonNode node, Set<String> indicatorIds) {
        if (node == null || node.isNull()) return null;

        String kind = node.has("kind") ? node.get("kind").asText() : null;

        if ("group".equals(kind)) {
            JsonNode children = node.get("children");
            if (children != null) {
                for (JsonNode child : children) {
                    String err = validateRuleNode(child, indicatorIds);
                    if (err != null) return err;
                }
            }
            return null;
        }

        if ("leaf".equals(kind)) {
            String type = node.get("type").asText();
            if (!SUPPORTED_RULES.contains(type)) {
                return "不支持的规则类型: " + type;
            }
            JsonNode indInputs = node.get("indicatorInputs");
            if (indInputs != null) {
                for (JsonNode input : indInputs) {
                    if (!indicatorIds.contains(input.asText())) {
                        return "规则引用了不存在的指标: " + input.asText();
                    }
                }
            }
            return null;
        }

        return null;
    }

    private String id(JsonNode node) {
        return node.has("id") ? node.get("id").asText() : "?";
    }

    private void buildIndicators(JsonNode indicatorsNode, BarSeries series,
                                  Map<String, Indicator<Num>> map,
                                  Map<String, Indicator<Boolean>> booleanMap) {
        if (indicatorsNode == null) return;
        List<JsonNode> sorted = topologicalSort(indicatorsNode);
        for (JsonNode ind : sorted) {
            String id = ind.get("id").asText();
            String type = ind.get("type").asText();
            if (SUPPORTED_BOOLEAN_INDICATORS.contains(type)) {
                Indicator<Boolean> indicator = createBooleanIndicator(type, ind, series, map, booleanMap);
                booleanMap.put(id, indicator);
            } else {
                Indicator<Num> indicator = createIndicator(type, ind, series, map, booleanMap);
                map.put(id, indicator);
            }
        }
    }

    private List<JsonNode> topologicalSort(JsonNode indicatorsNode) {
        List<JsonNode> nodes = new ArrayList<>();
        Map<String, JsonNode> nodeMap = new HashMap<>();
        Map<String, Set<String>> deps = new HashMap<>();

        for (JsonNode node : indicatorsNode) {
            String id = node.get("id").asText();
            nodes.add(node);
            nodeMap.put(id, node);
            Set<String> depSet = new HashSet<>();
            JsonNode inputs = node.get("inputs");
            if (inputs != null) {
                for (JsonNode input : inputs) {
                    depSet.add(input.asText());
                }
            }
            deps.put(id, depSet);
        }

        List<JsonNode> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (JsonNode node : nodes) {
            String id = node.get("id").asText();
            visit(id, nodeMap, deps, visited, visiting, sorted);
        }

        return sorted;
    }

    private void visit(String id, Map<String, JsonNode> nodeMap, Map<String, Set<String>> deps,
                       Set<String> visited, Set<String> visiting, List<JsonNode> sorted) {
        if (visited.contains(id)) return;
        if (visiting.contains(id)) {
            throw new RuntimeException("检测到指标循环依赖: " + id);
        }
        visiting.add(id);
        for (String dep : deps.getOrDefault(id, Set.of())) {
            visit(dep, nodeMap, deps, visited, visiting, sorted);
        }
        visiting.remove(id);
        visited.add(id);
        sorted.add(nodeMap.get(id));
    }

    @SuppressWarnings("unchecked")
    private Indicator<Num> createIndicator(String type, JsonNode ind, BarSeries series,
                                            Map<String, Indicator<Num>> map,
                                            Map<String, Indicator<Boolean>> booleanMap) {
        List<Indicator<Num>> inputs = resolveInputs(ind.get("inputs"), map);

        return switch (type) {
            case "ClosePriceIndicator" -> (Indicator<Num>) new ClosePriceIndicator(series);
            case "OpenPriceIndicator" -> (Indicator<Num>) new OpenPriceIndicator(series);
            case "HighPriceIndicator" -> (Indicator<Num>) new HighPriceIndicator(series);
            case "LowPriceIndicator" -> (Indicator<Num>) new LowPriceIndicator(series);
            case "VolumeIndicator" -> (Indicator<Num>) new VolumeIndicator(series);
            case "TypicalPriceIndicator" -> (Indicator<Num>) new TypicalPriceIndicator(series);
            case "MedianPriceIndicator" -> (Indicator<Num>) new MedianPriceIndicator(series);
            case "TRIndicator" -> (Indicator<Num>) new TRIndicator(series);

            case "SMAIndicator" -> (Indicator<Num>) new SMAIndicator(inputs.get(0), param(ind, "barCount", 20));
            case "EMAIndicator" -> (Indicator<Num>) new EMAIndicator(inputs.get(0), param(ind, "barCount", 20));
            case "WMAIndicator" -> (Indicator<Num>) new WMAIndicator(inputs.get(0), param(ind, "barCount", 20));
            case "LWMAIndicator" -> (Indicator<Num>) new LWMAIndicator(inputs.get(0), param(ind, "barCount", 20));

            case "RSIIndicator" -> (Indicator<Num>) new RSIIndicator(inputs.get(0), param(ind, "barCount", 14));
            case "MACDIndicator" -> (Indicator<Num>) new MACDIndicator(inputs.get(0), param(ind, "shortBarCount", 12), param(ind, "longBarCount", 26));
            case "ROCIndicator" -> (Indicator<Num>) new ROCIndicator(inputs.get(0), param(ind, "barCount", 12));
            case "PPOIndicator" -> (Indicator<Num>) new PPOIndicator(inputs.get(0), param(ind, "shortBarCount", 12), param(ind, "longBarCount", 26));
            case "CCIIndicator" -> (Indicator<Num>) new CCIIndicator(series, param(ind, "barCount", 20));
            case "ATRIndicator" -> (Indicator<Num>) new ATRIndicator(series, param(ind, "barCount", 14));
            case "WilliamsRIndicator" -> (Indicator<Num>) new WilliamsRIndicator(series, param(ind, "barCount", 14));
            case "DPOIndicator" -> (Indicator<Num>) new DPOIndicator(series, param(ind, "barCount", 20));
            case "ChopIndicator" -> (Indicator<Num>) new ChopIndicator(series, param(ind, "timeFrame", 14), param(ind, "scaleUpTo", 100));
            case "StochasticOscillatorKIndicator" -> (Indicator<Num>) new StochasticOscillatorKIndicator(series, param(ind, "barCount", 14));
            case "StochasticOscillatorDIndicator" -> {
                if (!inputs.isEmpty()) {
                    yield (Indicator<Num>) new StochasticOscillatorDIndicator(inputs.get(0));
                } else {
                    yield (Indicator<Num>) new StochasticOscillatorDIndicator(
                            new StochasticOscillatorKIndicator(series, 14));
                }
            }
            case "ParabolicSarIndicator" -> (Indicator<Num>) new ParabolicSarIndicator(series);

            case "IchimokuTenkanSenIndicator" -> (Indicator<Num>) new IchimokuTenkanSenIndicator(series, param(ind, "barCount", 9));
            case "IchimokuKijunSenIndicator" -> (Indicator<Num>) new IchimokuKijunSenIndicator(series, param(ind, "barCount", 26));
            case "IchimokuSenkouSpanAIndicator" -> (Indicator<Num>) new IchimokuSenkouSpanAIndicator(series, param(ind, "barCountTenkan", 9), param(ind, "barCountKijun", 26));
            case "IchimokuSenkouSpanBIndicator" -> (Indicator<Num>) new IchimokuSenkouSpanBIndicator(series, param(ind, "barCount", 52));
            case "IchimokuChikouSpanIndicator" -> (Indicator<Num>) new IchimokuChikouSpanIndicator(series, param(ind, "barCount", 26));

            case "StandardDeviationIndicator" -> (Indicator<Num>) new StandardDeviationIndicator(inputs.get(0), param(ind, "barCount", 20));
            case "VarianceIndicator" -> (Indicator<Num>) new VarianceIndicator(inputs.get(0), param(ind, "barCount", 20));
            case "CovarianceIndicator" -> (Indicator<Num>) new CovarianceIndicator(inputs.get(0), inputs.get(1), param(ind, "barCount", 20));
            case "CorrelationCoefficientIndicator" -> (Indicator<Num>) new CorrelationCoefficientIndicator(inputs.get(0), inputs.get(1), param(ind, "barCount", 20));
            case "SimpleLinearRegressionIndicator" -> (Indicator<Num>) new SimpleLinearRegressionIndicator(inputs.get(0), param(ind, "barCount", 20));

            case "ConstantIndicator" -> (Indicator<Num>) new ConstantIndicator<>(series, series.numFactory().numOf(param(ind, "value", 0)));
            case "PreviousValueIndicator" -> (Indicator<Num>) new PreviousValueIndicator(inputs.get(0), param(ind, "n", 1));

            case "BollingerBandsMiddleIndicator" -> (Indicator<Num>) new BollingerBandsMiddleIndicator(inputs.get(0));
            case "BollingerBandsUpperIndicator" -> {
                BollingerBandsMiddleIndicator bbm = (BollingerBandsMiddleIndicator) inputs.get(0);
                Indicator<Num> deviation = inputs.size() > 1 ? inputs.get(1) : new StandardDeviationIndicator(bbm.getIndicator(), 20);
                yield (Indicator<Num>) new BollingerBandsUpperIndicator(bbm, deviation);
            }
            case "BollingerBandsLowerIndicator" -> {
                BollingerBandsMiddleIndicator bbm = (BollingerBandsMiddleIndicator) inputs.get(0);
                Indicator<Num> deviation = inputs.size() > 1 ? inputs.get(1) : new StandardDeviationIndicator(bbm.getIndicator(), 20);
                yield (Indicator<Num>) new BollingerBandsLowerIndicator(bbm, deviation);
            }

            case "DifferenceIndicator" -> (Indicator<Num>) CombineIndicator.minus(inputs.get(0), inputs.get(1));
            case "SumIndicator" -> (Indicator<Num>) new SumIndicator(inputs.get(0), inputs.get(1));
            case "CombineIndicatorPlus" -> (Indicator<Num>) CombineIndicator.plus(inputs.get(0), inputs.get(1));
            case "CombineIndicatorMultiply" -> (Indicator<Num>) CombineIndicator.multiply(inputs.get(0), inputs.get(1));
            case "CombineIndicatorDivide" -> (Indicator<Num>) CombineIndicator.divide(inputs.get(0), inputs.get(1));

            case "OnBalanceVolumeIndicator" -> (Indicator<Num>) new OnBalanceVolumeIndicator(series);
            case "ChaikinMoneyFlowIndicator" -> (Indicator<Num>) new ChaikinMoneyFlowIndicator(series, param(ind, "barCount", 20));
            case "MoneyFlowIndexIndicator" -> (Indicator<Num>) new MoneyFlowIndexIndicator(series, param(ind, "barCount", 14));
            case "AccumulationDistributionIndicator" -> (Indicator<Num>) new AccumulationDistributionIndicator(series);
            case "NVIIndicator" -> (Indicator<Num>) new NVIIndicator(series);
            case "PVIIndicator" -> (Indicator<Num>) new PVIIndicator(series);

            case "WeightedCompositeIndicator" -> {
                List<Indicator<Num>> factorIndList = resolveInputs(ind.get("inputs"), map);
                JsonNode weightsNode = ind.get("params") != null ? ind.get("params").get("weights") : null;
                List<Double> weightList = new ArrayList<>();
                if (weightsNode != null && weightsNode.isArray()) {
                    for (JsonNode w : weightsNode) {
                        weightList.add(w.asDouble());
                    }
                }
                int lookback = param(ind, "lookbackPeriod", 120);
                yield (Indicator<Num>) new WeightedCompositeIndicator(series, factorIndList, weightList, lookback);
            }

            default -> throw new IllegalArgumentException("不支持的指标类型: " + type);
        };
    }

    private Indicator<Boolean> createBooleanIndicator(String type, JsonNode ind, BarSeries series,
                                                       Map<String, Indicator<Num>> map,
                                                       Map<String, Indicator<Boolean>> booleanMap) {
        return switch (type) {
            case "BullishEngulfingIndicator" -> new BullishEngulfingIndicator(series);
            case "BearishEngulfingIndicator" -> new BearishEngulfingIndicator(series);
            case "HammerIndicator" -> new HammerIndicator(series);
            case "DojiIndicator" -> new DojiIndicator(series, param(ind, "barCount", 10), param(ind, "factor", 0.1));
            case "MorningStarIndicator" -> new MorningStarIndicator(series);
            case "EveningStarIndicator" -> new EveningStarIndicator(series);
            default -> throw new IllegalArgumentException("不支持的布尔指标类型: " + type);
        };
    }

    private List<Indicator<Num>> resolveInputs(JsonNode inputsNode, Map<String, Indicator<Num>> map) {
        List<Indicator<Num>> result = new ArrayList<>();
        if (inputsNode != null) {
            for (JsonNode input : inputsNode) {
                Indicator<Num> ind = map.get(input.asText());
                if (ind == null) {
                    throw new IllegalArgumentException("未找到指标: " + input.asText());
                }
                result.add(ind);
            }
        }
        return result;
    }

    private Rule buildRulesFromConditions(JsonNode conditionsNode, String combinator,
                                           Map<String, Indicator<Num>> indicatorMap,
                                           Map<String, Indicator<Boolean>> booleanIndicatorMap,
                                           BarSeries series) {
        if (conditionsNode == null || !conditionsNode.isArray() || conditionsNode.isEmpty()) {
            return org.ta4j.core.rules.BooleanRule.FALSE;
        }

        List<Rule> rules = new ArrayList<>();
        for (JsonNode cond : conditionsNode) {
            Rule rule = buildLeafRule(cond, indicatorMap, booleanIndicatorMap, series);
            rules.add(rule);
        }

        if (rules.size() == 1) {
            return rules.get(0);
        }

        Rule combined = rules.get(0);
        for (int i = 1; i < rules.size(); i++) {
            if ("OR".equalsIgnoreCase(combinator)) {
                combined = new OrRule(combined, rules.get(i));
            } else {
                combined = new AndRule(combined, rules.get(i));
            }
        }
        return combined;
    }

    private Rule buildRuleFromNode(JsonNode node, Map<String, Indicator<Num>> indicatorMap,
                                    Map<String, Indicator<Boolean>> booleanIndicatorMap,
                                    BarSeries series) {
        if (node == null || node.isNull()) {
            return org.ta4j.core.rules.BooleanRule.FALSE;
        }

        String kind = node.has("kind") ? node.get("kind").asText() : null;

        if ("group".equals(kind)) {
            JsonNode children = node.get("children");
            String combinator = node.has("combinator") ? node.get("combinator").asText("AND") : "AND";

            if (children == null || children.isEmpty()) {
                return org.ta4j.core.rules.BooleanRule.FALSE;
            }

            List<Rule> rules = new ArrayList<>();
            for (JsonNode child : children) {
                rules.add(buildRuleFromNode(child, indicatorMap, booleanIndicatorMap, series));
            }

            Rule combined;
            if (rules.size() == 1) {
                combined = rules.get(0);
            } else {
                combined = rules.get(0);
                for (int i = 1; i < rules.size(); i++) {
                    if ("OR".equalsIgnoreCase(combinator)) {
                        combined = new OrRule(combined, rules.get(i));
                    } else {
                        combined = new AndRule(combined, rules.get(i));
                    }
                }
            }

            boolean negated = node.has("negated") && node.get("negated").asBoolean(false);
            return negated ? new NotRule(combined) : combined;
        }

        if ("leaf".equals(kind)) {
            Rule rule = buildLeafRule(node, indicatorMap, booleanIndicatorMap, series);
            boolean negated = node.has("negated") && node.get("negated").asBoolean(false);
            return negated ? new NotRule(rule) : rule;
        }

        return org.ta4j.core.rules.BooleanRule.FALSE;
    }

    private Rule buildLeafRule(JsonNode cond, Map<String, Indicator<Num>> indicatorMap,
                               Map<String, Indicator<Boolean>> booleanIndicatorMap,
                               BarSeries series) {
        String type = cond.get("type").asText();

        if ("BooleanIndicatorRule".equals(type)) {
            JsonNode indInputs = cond.get("indicatorInputs");
            if (indInputs != null && !indInputs.isEmpty()) {
                String boolIndId = indInputs.get(0).asText();
                Indicator<Boolean> boolInd = booleanIndicatorMap.get(boolIndId);
                if (boolInd != null) {
                    return new BooleanIndicatorRule(boolInd);
                }
            }
            throw new IllegalArgumentException("BooleanIndicatorRule 需要引用布尔类型指标");
        }

        List<Indicator<Num>> indInputs = resolveInputs(cond.get("indicatorInputs"), indicatorMap);

        return switch (type) {
            case "CrossedUpIndicatorRule" -> new CrossedUpIndicatorRule(indInputs.get(0), indInputs.get(1));
            case "CrossedDownIndicatorRule" -> new CrossedDownIndicatorRule(indInputs.get(0), indInputs.get(1));
            case "OverIndicatorRule" -> new OverIndicatorRule(indInputs.get(0), indInputs.get(1));
            case "UnderIndicatorRule" -> new UnderIndicatorRule(indInputs.get(0), indInputs.get(1));

            case "StopLossRule" -> new StopLossRule(indInputs.get(0), param(cond, "lossPercentage", 5.0));
            case "StopGainRule" -> new StopGainRule(indInputs.get(0), param(cond, "gainPercentage", 10.0));
            case "TrailingStopLossRule" -> new TrailingStopLossRule(indInputs.get(0), series.numFactory().numOf(param(cond, "lossPercentage", 5.0)));

            case "IsRisingRule" -> new IsRisingRule(indInputs.get(0), param(cond, "barCount", 3));
            case "IsFallingRule" -> new IsFallingRule(indInputs.get(0), param(cond, "barCount", 3));
            case "IsHighestRule" -> new IsHighestRule(indInputs.get(0), param(cond, "barCount", 20));
            case "IsLowestRule" -> new IsLowestRule(indInputs.get(0), param(cond, "barCount", 20));

            case "InPipeRule" -> new InPipeRule(indInputs.get(0), param(cond, "lower", 0.0), param(cond, "upper", 100.0));

            case "MaxTradeBarCountRule" -> new MaxTradeBarCountRule(param(cond, "maxBarCount", 5));

            default -> throw new IllegalArgumentException("不支持的规则类型: " + type);
        };
    }

    private int param(JsonNode node, String name, int defaultVal) {
        JsonNode params = node.get("params");
        if (params != null && params.has(name)) {
            return params.get(name).asInt(defaultVal);
        }
        return defaultVal;
    }

    private double param(JsonNode node, String name, double defaultVal) {
        JsonNode params = node.get("params");
        if (params != null && params.has(name)) {
            return params.get(name).asDouble(defaultVal);
        }
        return defaultVal;
    }

    private static final Set<String> SUPPORTED_INDICATORS = Set.of(
            "ClosePriceIndicator", "OpenPriceIndicator", "HighPriceIndicator", "LowPriceIndicator",
            "VolumeIndicator", "TypicalPriceIndicator", "MedianPriceIndicator", "TRIndicator",
            "SMAIndicator", "EMAIndicator", "WMAIndicator", "LWMAIndicator",
            "RSIIndicator", "MACDIndicator", "ROCIndicator", "PPOIndicator",
            "CCIIndicator", "ATRIndicator", "WilliamsRIndicator", "DPOIndicator",
            "ChopIndicator", "StochasticOscillatorKIndicator", "StochasticOscillatorDIndicator",
            "ParabolicSarIndicator",
            "IchimokuTenkanSenIndicator", "IchimokuKijunSenIndicator",
            "IchimokuSenkouSpanAIndicator", "IchimokuSenkouSpanBIndicator", "IchimokuChikouSpanIndicator",
            "StandardDeviationIndicator", "VarianceIndicator",
            "CovarianceIndicator", "CorrelationCoefficientIndicator", "SimpleLinearRegressionIndicator",
            "ConstantIndicator", "PreviousValueIndicator",
            "BollingerBandsMiddleIndicator", "BollingerBandsUpperIndicator", "BollingerBandsLowerIndicator",
            "DifferenceIndicator", "SumIndicator",
            "CombineIndicatorPlus", "CombineIndicatorMultiply", "CombineIndicatorDivide",
            "OnBalanceVolumeIndicator", "ChaikinMoneyFlowIndicator", "MoneyFlowIndexIndicator",
            "AccumulationDistributionIndicator", "NVIIndicator", "PVIIndicator",
            "WeightedCompositeIndicator"
    );

    private static final Set<String> SUPPORTED_BOOLEAN_INDICATORS = Set.of(
            "BullishEngulfingIndicator", "BearishEngulfingIndicator",
            "HammerIndicator", "DojiIndicator",
            "MorningStarIndicator", "EveningStarIndicator"
    );

    private static final Set<String> SUPPORTED_RULES = Set.of(
            "CrossedUpIndicatorRule", "CrossedDownIndicatorRule", "OverIndicatorRule", "UnderIndicatorRule",
            "StopLossRule", "StopGainRule", "TrailingStopLossRule",
            "IsRisingRule", "IsFallingRule", "IsHighestRule", "IsLowestRule",
            "InPipeRule", "BooleanIndicatorRule",
            "MaxTradeBarCountRule"
    );
}
