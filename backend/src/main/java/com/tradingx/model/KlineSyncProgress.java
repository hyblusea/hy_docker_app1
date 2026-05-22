package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class KlineSyncProgress {
    private String taskId;
    private int current;
    private int total;
    private String currentStock;
    private String currentPeriod;
    private boolean completed;
    private boolean cancelled;
    private int successCount;
    private int failCount;
    private String initError;

    @JsonProperty("taskId")
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    public int getCurrent() { return current; }
    public void setCurrent(int current) { this.current = current; }
    
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    
    @JsonProperty("current_stock")
    public String getCurrentStock() { return currentStock; }
    public void setCurrentStock(String currentStock) { this.currentStock = currentStock; }
    
    @JsonProperty("current_period")
    public String getCurrentPeriod() { return currentPeriod; }
    public void setCurrentPeriod(String currentPeriod) { this.currentPeriod = currentPeriod; }
    
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    
    @JsonProperty("success_count")
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    
    @JsonProperty("fail_count")
    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }
    
    @JsonProperty("init_error")
    public String getInitError() { return initError; }
    public void setInitError(String initError) { this.initError = initError; }
}
