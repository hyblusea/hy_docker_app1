package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class KlineRangeInfo {
    private String period;
    private String startDate;
    private String endDate;
    private Integer stockCount;
    private Long totalRecords;

    @JsonProperty("period")
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    
    @JsonProperty("start_date")
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    
    @JsonProperty("end_date")
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    
    @JsonProperty("stock_count")
    public Integer getStockCount() { return stockCount; }
    public void setStockCount(Integer stockCount) { this.stockCount = stockCount; }
    
    @JsonProperty("total_records")
    public Long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Long totalRecords) { this.totalRecords = totalRecords; }
}
