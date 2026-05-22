package com.tradingx.model;

import java.util.List;

public class BacktestRequest {
    private Long strategyId;
    private String tsCode;
    private String startDate;
    private String endDate;
    private List<DailyQuote> quotes;
    private String visualStrategy;

    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }
    public String getTsCode() { return tsCode; }
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public List<DailyQuote> getQuotes() { return quotes; }
    public void setQuotes(List<DailyQuote> quotes) { this.quotes = quotes; }
    public String getVisualStrategy() { return visualStrategy; }
    public void setVisualStrategy(String visualStrategy) { this.visualStrategy = visualStrategy; }
}
