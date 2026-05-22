package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class BatchBacktestRequest {
    private String tsCode;
    private String startDate;
    private String endDate;
    private List<Long> strategyIds;

    @JsonProperty("ts_code")
    public String getTsCode() { return tsCode; }
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }

    @JsonProperty("start_date")
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    @JsonProperty("end_date")
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    @JsonProperty("strategy_ids")
    public List<Long> getStrategyIds() { return strategyIds; }
    public void setStrategyIds(List<Long> strategyIds) { this.strategyIds = strategyIds; }
}
