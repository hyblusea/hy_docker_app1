package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class MissingDataResult {
    private int totalStocks;
    private int completeStocks;
    private int incompleteStocks;
    private double avgCompletionRate;
    private List<MissingDetail> missingDetails;

    public static class MissingDetail {
        private String tsCode;
        private String stockName;
        private String period;
        private int missingDays;
        private String firstMissingDate;
        private String lastMissingDate;

        @JsonProperty("ts_code")
        public String getTsCode() { return tsCode; }
        public void setTsCode(String tsCode) { this.tsCode = tsCode; }
        
        @JsonProperty("stock_name")
        public String getStockName() { return stockName; }
        public void setStockName(String stockName) { this.stockName = stockName; }
        
        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        
        @JsonProperty("missing_days")
        public int getMissingDays() { return missingDays; }
        public void setMissingDays(int missingDays) { this.missingDays = missingDays; }
        
        @JsonProperty("first_missing_date")
        public String getFirstMissingDate() { return firstMissingDate; }
        public void setFirstMissingDate(String firstMissingDate) { this.firstMissingDate = firstMissingDate; }
        
        @JsonProperty("last_missing_date")
        public String getLastMissingDate() { return lastMissingDate; }
        public void setLastMissingDate(String lastMissingDate) { this.lastMissingDate = lastMissingDate; }
    }

    @JsonProperty("total_stocks")
    public int getTotalStocks() { return totalStocks; }
    public void setTotalStocks(int totalStocks) { this.totalStocks = totalStocks; }
    
    @JsonProperty("complete_stocks")
    public int getCompleteStocks() { return completeStocks; }
    public void setCompleteStocks(int completeStocks) { this.completeStocks = completeStocks; }
    
    @JsonProperty("incomplete_stocks")
    public int getIncompleteStocks() { return incompleteStocks; }
    public void setIncompleteStocks(int incompleteStocks) { this.incompleteStocks = incompleteStocks; }
    
    @JsonProperty("avg_completion_rate")
    public double getAvgCompletionRate() { return avgCompletionRate; }
    public void setAvgCompletionRate(double avgCompletionRate) { this.avgCompletionRate = avgCompletionRate; }
    
    @JsonProperty("missing_details")
    public List<MissingDetail> getMissingDetails() { return missingDetails; }
    public void setMissingDetails(List<MissingDetail> missingDetails) { this.missingDetails = missingDetails; }
}
