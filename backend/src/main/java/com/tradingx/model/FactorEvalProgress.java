package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FactorEvalProgress {

    @JsonProperty("task_id")
    private String taskId;

    private int current;
    private int total;

    @JsonProperty("current_stock")
    private String currentStock;

    @JsonProperty("current_factor")
    private String currentFactor;

    private boolean completed;
    private boolean cancelled;

    @JsonProperty("factor_completed")
    private int factorCompleted;

    @JsonProperty("total_factors")
    private int totalFactors;

    @JsonProperty("init_error")
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

    @JsonProperty("current_factor")
    public String getCurrentFactor() { return currentFactor; }
    public void setCurrentFactor(String currentFactor) { this.currentFactor = currentFactor; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    @JsonProperty("factor_completed")
    public int getFactorCompleted() { return factorCompleted; }
    public void setFactorCompleted(int factorCompleted) { this.factorCompleted = factorCompleted; }

    @JsonProperty("total_factors")
    public int getTotalFactors() { return totalFactors; }
    public void setTotalFactors(int totalFactors) { this.totalFactors = totalFactors; }

    @JsonProperty("init_error")
    public String getInitError() { return initError; }
    public void setInitError(String initError) { this.initError = initError; }
}
