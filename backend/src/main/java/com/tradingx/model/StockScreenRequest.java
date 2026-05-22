package com.tradingx.model;

import java.util.List;

public class StockScreenRequest {
    private String startDate;
    private String endDate;
    private List<Long> strategyIds;
    private String username;
    private String screenMode;

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public List<Long> getStrategyIds() { return strategyIds; }
    public void setStrategyIds(List<Long> strategyIds) { this.strategyIds = strategyIds; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getScreenMode() { return screenMode; }
    public void setScreenMode(String screenMode) { this.screenMode = screenMode; }
}
