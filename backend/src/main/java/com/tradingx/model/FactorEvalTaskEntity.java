package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 因子评估任务实体类
 * 存储因子评估任务的基本信息和执行状态
 */
@Entity
@Table(name = "factor_eval_task", comment = "因子评估任务表 - 存储因子评估任务的基本信息和执行状态")
public class FactorEvalTaskEntity {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务唯一标识
     */
    @Column(name = "task_id", nullable = false, unique = true, comment = "任务唯一标识")
    private String taskId;

    /**
     * 用户名
     */
    @Column(name = "username", length = 50, comment = "用户名")
    private String username;

    /**
     * 开始日期
     */
    @Column(name = "start_date", length = 8, comment = "开始日期")
    private String startDate;

    /**
     * 结束日期
     */
    @Column(name = "end_date", length = 8, comment = "结束日期")
    private String endDate;

    /**
     * 因子名称列表（JSON格式）
     */
    @Column(name = "factor_names", columnDefinition = "TEXT", comment = "因子名称列表")
    private String factorNames;

    /**
     * 向前预测天数
     */
    @Column(name = "forward_days", comment = "向前预测天数")
    private Integer forwardDays;

    /**
     * 总股票数量
     */
    @Column(name = "total_stocks", comment = "总股票数量")
    private Integer totalStocks;

    /**
     * 是否完成
     */
    @Column(nullable = false, comment = "是否完成")
    private Boolean completed = false;

    /**
     * 初始化错误信息
     */
    @Column(name = "init_error", columnDefinition = "TEXT", comment = "初始化错误信息")
    private String initError;

    /**
     * 评估结果JSON数据
     */
    @Column(name = "results_json", columnDefinition = "LONGTEXT", comment = "评估结果JSON数据")
    private String resultsJson;

    /**
     * 因子组合结果JSON数据
     */
    @Column(name = "combination_json", columnDefinition = "TEXT", comment = "因子组合结果JSON数据")
    private String combinationJson;

    /**
     * 创建时间
     */
    @Column(name = "created_at", updatable = false, comment = "创建时间")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", comment = "更新时间")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    @JsonProperty("factor_names")
    public String getFactorNames() { return factorNames; }
    public void setFactorNames(String factorNames) { this.factorNames = factorNames; }

    @JsonProperty("forward_days")
    public Integer getForwardDays() { return forwardDays; }
    public void setForwardDays(Integer forwardDays) { this.forwardDays = forwardDays; }

    @JsonProperty("total_stocks")
    public Integer getTotalStocks() { return totalStocks; }
    public void setTotalStocks(Integer totalStocks) { this.totalStocks = totalStocks; }

    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }

    @JsonProperty("init_error")
    public String getInitError() { return initError; }
    public void setInitError(String initError) { this.initError = initError; }

    @JsonProperty("results_json")
    public String getResultsJson() { return resultsJson; }
    public void setResultsJson(String resultsJson) { this.resultsJson = resultsJson; }

    @JsonProperty("combination_json")
    public String getCombinationJson() { return combinationJson; }
    public void setCombinationJson(String combinationJson) { this.combinationJson = combinationJson; }

    @JsonProperty("created_at")
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @JsonProperty("updated_at")
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}