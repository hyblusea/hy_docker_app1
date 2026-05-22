package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 选股匹配结果实体类
 * 存储策略选股任务的匹配结果详情
 */
@Entity
@Table(name = "screen_match", indexes = {
    @Index(name = "idx_screen_match_task_id", columnList = "task_id"), 
    @Index(name = "idx_screen_match_task_ts_strategy", columnList = "task_id, ts_code, strategy_id")
}, comment = "选股匹配结果表 - 存储策略选股任务的匹配结果详情")
public class ScreenMatchEntity {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务ID
     */
    @Column(name = "task_id", comment = "任务ID")
    private String taskId;

    /**
     * 股票代码
     */
    @Column(name = "ts_code", length = 20, comment = "股票代码")
    private String tsCode;

    /**
     * 股票名称
     */
    @Column(name = "stock_name", length = 50, comment = "股票名称")
    private String name;

    /**
     * 策略ID
     */
    @Column(name = "strategy_id", comment = "策略ID")
    private Long strategyId;

    /**
     * 策略名称
     */
    @Column(name = "strategy_name", length = 100, comment = "策略名称")
    private String strategyName;

    /**
     * 总收益率
     */
    @Column(name = "total_return", comment = "总收益率")
    private Double totalReturn;

    /**
     * 胜率
     */
    @Column(name = "win_rate", comment = "胜率")
    private Double winRate;

    /**
     * 交易次数
     */
    @Column(name = "trade_count", comment = "交易次数")
    private Integer tradeCount;

    /**
     * 盈亏金额
     */
    @Column(name = "profit_loss", comment = "盈亏金额")
    private Double profitLoss;

    /**
     * 最大回撤
     */
    @Column(name = "max_drawdown", comment = "最大回撤")
    private Double maxDrawdown;

    /**
     * 持仓数量
     */
    @Column(name = "open_position_count", comment = "持仓数量")
    private Integer openPositionCount;

    /**
     * 初始资金
     */
    @Column(name = "initial_capital", comment = "初始资金")
    private Double initialCapital;

    /**
     * 最终资金
     */
    @Column(name = "final_capital", comment = "最终资金")
    private Double finalCapital;

    /**
     * 总手续费
     */
    @Column(name = "total_fees", comment = "总手续费")
    private Double totalFees;

    /**
     * 信号JSON数据
     */
    @Column(name = "signals_json", columnDefinition = "TEXT", comment = "信号JSON数据")
    private String signalsJson;

    /**
     * 创建时间
     */
    @Column(name = "created_at", updatable = false, comment = "创建时间")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @JsonProperty("task_id")
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    @JsonProperty("ts_code")
    public String getTsCode() { return tsCode; }
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }
    
    @JsonProperty("name")
    public String getName() { return name; }
    @JsonProperty("name")
    public void setName(String name) { this.name = name; }
    
    @JsonProperty("strategy_id")
    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }
    
    @JsonProperty("strategy_name")
    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }
    
    @JsonProperty("total_return")
    public Double getTotalReturn() { return totalReturn; }
    public void setTotalReturn(Double totalReturn) { this.totalReturn = totalReturn; }
    
    @JsonProperty("win_rate")
    public Double getWinRate() { return winRate; }
    public void setWinRate(Double winRate) { this.winRate = winRate; }
    
    @JsonProperty("trade_count")
    public Integer getTradeCount() { return tradeCount; }
    public void setTradeCount(Integer tradeCount) { this.tradeCount = tradeCount; }
    
    @JsonProperty("profit_loss")
    public Double getProfitLoss() { return profitLoss; }
    public void setProfitLoss(Double profitLoss) { this.profitLoss = profitLoss; }
    
    @JsonProperty("max_drawdown")
    public Double getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(Double maxDrawdown) { this.maxDrawdown = maxDrawdown; }
    
    @JsonProperty("open_position_count")
    public Integer getOpenPositionCount() { return openPositionCount; }
    public void setOpenPositionCount(Integer openPositionCount) { this.openPositionCount = openPositionCount; }
    
    @JsonProperty("initial_capital")
    public Double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(Double initialCapital) { this.initialCapital = initialCapital; }
    
    @JsonProperty("final_capital")
    public Double getFinalCapital() { return finalCapital; }
    public void setFinalCapital(Double finalCapital) { this.finalCapital = finalCapital; }
    
    @JsonProperty("total_fees")
    public Double getTotalFees() { return totalFees; }
    public void setTotalFees(Double totalFees) { this.totalFees = totalFees; }
    
    @JsonProperty("signals_json")
    public String getSignalsJson() { return signalsJson; }
    public void setSignalsJson(String signalsJson) { this.signalsJson = signalsJson; }
    
    @JsonProperty("created_at")
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}