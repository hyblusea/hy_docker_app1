package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StrategyBacktestResult {

    @JsonProperty("strategy_id")
    private Long strategyId;

    @JsonProperty("strategy_name")
    private String strategyName;

    private BacktestResult result;

    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }

    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    public BacktestResult getResult() { return result; }
    public void setResult(BacktestResult result) { this.result = result; }
}
