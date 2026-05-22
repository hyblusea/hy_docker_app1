
package com.tradingx.model.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ml_backtest", indexes = {
    @Index(name = "idx_ml_backtest_model_version_id", columnList = "model_version_id")
})
public class BacktestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_version_id", nullable = false)
    @JsonProperty("modelVersionId")
    private Long modelVersionId;

    @Column(name = "backtest_name", length = 128)
    @JsonProperty("backtestName")
    private String backtestName;

    @Column(name = "start_date", nullable = false, length = 8)
    @JsonProperty("startDate")
    private String startDate;

    @Column(name = "end_date", nullable = false, length = 8)
    @JsonProperty("endDate")
    private String endDate;

    @Column(name = "initial_capital", precision = 16, scale = 2)
    @JsonProperty("initialCapital")
    private BigDecimal initialCapital;

    @Column(name = "final_capital", precision = 16, scale = 2)
    @JsonProperty("finalCapital")
    private BigDecimal finalCapital;

    @Column(name = "total_return")
    @JsonProperty("totalReturn")
    private Double totalReturn;

    @Column(name = "annual_return")
    @JsonProperty("annualReturn")
    private Double annualReturn;

    @Column(name = "max_drawdown")
    @JsonProperty("maxDrawdown")
    private Double maxDrawdown;

    @Column(name = "sharpe_ratio")
    @JsonProperty("sharpeRatio")
    private Double sharpeRatio;

    @Column(name = "sortino_ratio")
    @JsonProperty("sortinoRatio")
    private Double sortinoRatio;

    @Column(name = "calmar_ratio")
    @JsonProperty("calmarRatio")
    private Double calmarRatio;

    @Column(name = "win_rate")
    @JsonProperty("winRate")
    private Double winRate;

    @Column(name = "profit_factor")
    @JsonProperty("profitFactor")
    private Double profitFactor;

    @Column(name = "total_trades")
    @JsonProperty("totalTrades")
    private Integer totalTrades;

    @Column(name = "avg_holding_days")
    @JsonProperty("avgHoldingDays")
    private Double avgHoldingDays;

    @Column(name = "config_json", columnDefinition = "TEXT")
    @JsonProperty("configJson")
    private String configJson;

    @Column(name = "daily_nav_json", columnDefinition = "TEXT")
    @JsonProperty("dailyNavJson")
    private String dailyNavJson;

    @Column(name = "trade_log_json", columnDefinition = "TEXT")
    @JsonProperty("tradeLogJson")
    private String tradeLogJson;

    @Column(name = "status", length = 16)
    @JsonProperty("status")
    private String status;

    @Column(name = "created_at", updatable = false)
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    @JsonProperty("completedAt")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        status = "RUNNING";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getModelVersionId() { return modelVersionId; }
    public void setModelVersionId(Long modelVersionId) { this.modelVersionId = modelVersionId; }

    public String getBacktestName() { return backtestName; }
    public void setBacktestName(String backtestName) { this.backtestName = backtestName; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public BigDecimal getInitialCapital() { return initialCapital; }
    public void setInitialCapital(BigDecimal initialCapital) { this.initialCapital = initialCapital; }

    public BigDecimal getFinalCapital() { return finalCapital; }
    public void setFinalCapital(BigDecimal finalCapital) { this.finalCapital = finalCapital; }

    public Double getTotalReturn() { return totalReturn; }
    public void setTotalReturn(Double totalReturn) { this.totalReturn = totalReturn; }

    public Double getAnnualReturn() { return annualReturn; }
    public void setAnnualReturn(Double annualReturn) { this.annualReturn = annualReturn; }

    public Double getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(Double maxDrawdown) { this.maxDrawdown = maxDrawdown; }

    public Double getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(Double sharpeRatio) { this.sharpeRatio = sharpeRatio; }

    public Double getSortinoRatio() { return sortinoRatio; }
    public void setSortinoRatio(Double sortinoRatio) { this.sortinoRatio = sortinoRatio; }

    public Double getCalmarRatio() { return calmarRatio; }
    public void setCalmarRatio(Double calmarRatio) { this.calmarRatio = calmarRatio; }

    public Double getWinRate() { return winRate; }
    public void setWinRate(Double winRate) { this.winRate = winRate; }

    public Double getProfitFactor() { return profitFactor; }
    public void setProfitFactor(Double profitFactor) { this.profitFactor = profitFactor; }

    public Integer getTotalTrades() { return totalTrades; }
    public void setTotalTrades(Integer totalTrades) { this.totalTrades = totalTrades; }

    public Double getAvgHoldingDays() { return avgHoldingDays; }
    public void setAvgHoldingDays(Double avgHoldingDays) { this.avgHoldingDays = avgHoldingDays; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public String getDailyNavJson() { return dailyNavJson; }
    public void setDailyNavJson(String dailyNavJson) { this.dailyNavJson = dailyNavJson; }

    public String getTradeLogJson() { return tradeLogJson; }
    public void setTradeLogJson(String tradeLogJson) { this.tradeLogJson = tradeLogJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}

