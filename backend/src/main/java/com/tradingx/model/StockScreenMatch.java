package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class StockScreenMatch {
    private String tsCode;
    private String name;
    private Long strategyId;
    private String strategyName;
    private List<DailyQuote> quotes;
    private BacktestResult result;

    @JsonProperty("ts_code")
    public String getTsCode() { return tsCode; }
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }
    
    @JsonProperty("name")
    public String getName() { return name; }
    @JsonProperty("name")
    public void setName(String name) { this.name = name; }
    
    @JsonProperty("strategy_id")
    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }
    
    @JsonProperty("strategy_name")
    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }
    
    public List<DailyQuote> getQuotes() { return quotes; }
    public void setQuotes(List<DailyQuote> quotes) { this.quotes = quotes; }
    
    public BacktestResult getResult() { return result; }
    public void setResult(BacktestResult result) { this.result = result; }
}
