package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public class AnalysisTaskSummary {

    private Long id;
    private String taskId;
    private String username;
    private String startDate;
    private String endDate;
    private Integer strategyCount;
    private Integer totalStocks;
    private Boolean completed;
    private String initError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public AnalysisTaskSummary(Long id, String taskId, String username, String startDate,
                               String endDate, Integer strategyCount, Integer totalStocks,
                               Boolean completed, String initError,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.taskId = taskId;
        this.username = username;
        this.startDate = startDate;
        this.endDate = endDate;
        this.strategyCount = strategyCount;
        this.totalStocks = totalStocks;
        this.completed = completed;
        this.initError = initError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @JsonProperty("task_id")
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    @JsonProperty("start_date")
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    @JsonProperty("end_date")
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    @JsonProperty("strategy_count")
    public Integer getStrategyCount() { return strategyCount; }
    public void setStrategyCount(Integer strategyCount) { this.strategyCount = strategyCount; }

    @JsonProperty("total_stocks")
    public Integer getTotalStocks() { return totalStocks; }
    public void setTotalStocks(Integer totalStocks) { this.totalStocks = totalStocks; }

    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }

    @JsonProperty("init_error")
    public String getInitError() { return initError; }
    public void setInitError(String initError) { this.initError = initError; }

    @JsonProperty("created_at")
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @JsonProperty("updated_at")
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
