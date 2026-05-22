package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * K线同步状态实体类
 * 存储股票K线数据同步状态信息
 */
@Entity
@Table(name = "kline_sync_status", indexes = {
    @Index(name = "idx_sync_ts_period", columnList = "ts_code, period", unique = true)
}, comment = "K线同步状态表 - 存储股票K线数据同步状态信息")
public class KlineSyncStatusEntity {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 股票代码
     */
    @Column(name = "ts_code", nullable = false, length = 20, comment = "股票代码")
    private String tsCode;

    /**
     * 周期类型（如 D-日线, W-周线, M-月线）
     */
    @Column(nullable = false, length = 10, comment = "周期类型")
    private String period;

    /**
     * 股票名称
     */
    @Column(name = "stock_name", length = 100, comment = "股票名称")
    private String stockName;

    /**
     * 最后同步日期
     */
    @Column(name = "last_sync_date", length = 8, comment = "最后同步日期")
    private String lastSyncDate;

    /**
     * 数据起始日期
     */
    @Column(name = "start_date", length = 8, comment = "数据起始日期")
    private String startDate;

    /**
     * 同步状态
     */
    @Column(nullable = false, length = 20, comment = "同步状态")
    private String status;

    /**
     * 总记录数
     */
    @Column(name = "total_records", comment = "总记录数")
    private Integer totalRecords;

    /**
     * 数据年限
     */
    @Column(name = "data_years", comment = "数据年限")
    private Integer dataYears;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT", comment = "错误信息")
    private String errorMessage;

    /**
     * 连续失败次数
     */
    @Column(name = "consecutive_failures", comment = "连续失败次数")
    private Integer consecutiveFailures;

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
    
    @JsonProperty("ts_code")
    public String getTsCode() { return tsCode; }
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }
    
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    
    @JsonProperty("stock_name")
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    
    @JsonProperty("last_sync_date")
    public String getLastSyncDate() { return lastSyncDate; }
    public void setLastSyncDate(String lastSyncDate) { this.lastSyncDate = lastSyncDate; }
    
    @JsonProperty("start_date")
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    @JsonProperty("total_records")
    public Integer getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Integer totalRecords) { this.totalRecords = totalRecords; }
    
    @JsonProperty("data_years")
    public Integer getDataYears() { return dataYears; }
    public void setDataYears(Integer dataYears) { this.dataYears = dataYears; }
    
    @JsonProperty("error_message")
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    @JsonProperty("consecutive_failures")
    public Integer getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(Integer consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    
    @JsonProperty("created_at")
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    @JsonProperty("updated_at")
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}