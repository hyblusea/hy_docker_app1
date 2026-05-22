package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StockScreenProgress {
    private String taskId;
    private int current;
    private int total;
    private String currentStock;
    private boolean completed;
    private boolean cancelled;
    private int matchCount;
    private String initError;

    @JsonProperty("task_id")
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    public int getCurrent() { return current; }
    public void setCurrent(int current) { this.current = current; }
    
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    
    @JsonProperty("current_stock")
    public String getCurrentStock() { return currentStock; }
    public void setCurrentStock(String currentStock) { this.currentStock = currentStock; }
    
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    
    @JsonProperty("match_count")
    public int getMatchCount() { return matchCount; }
    public void setMatchCount(int matchCount) { this.matchCount = matchCount; }
    
    @JsonProperty("init_error")
    public String getInitError() { return initError; }
    public void setInitError(String initError) { this.initError = initError; }
}
