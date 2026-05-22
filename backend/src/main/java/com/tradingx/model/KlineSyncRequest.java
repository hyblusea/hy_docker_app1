package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class KlineSyncRequest {
    private List<String> periods;
    private List<String> tsCodes;
    private String startDate;
    private String endDate;

    public List<String> getPeriods() { return periods; }
    public void setPeriods(List<String> periods) { this.periods = periods; }
    
    @JsonProperty("ts_codes")
    public List<String> getTsCodes() { return tsCodes; }
    public void setTsCodes(List<String> tsCodes) { this.tsCodes = tsCodes; }
    
    @JsonProperty("start_date")
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    
    @JsonProperty("end_date")
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
}
