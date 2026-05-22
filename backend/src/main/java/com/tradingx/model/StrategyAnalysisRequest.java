package com.tradingx.model;

import java.util.List;

public class StrategyAnalysisRequest {
    private String username;
    private String startDate;
    private String endDate;
    private List<Long> strategyIds;
    private Long strategyId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public List<Long> getStrategyIds() { return strategyIds; }
    public void setStrategyIds(List<Long> strategyIds) { this.strategyIds = strategyIds; }
    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }
}
