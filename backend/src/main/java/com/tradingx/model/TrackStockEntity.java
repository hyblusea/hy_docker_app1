package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票跟踪实体类
 * 存储用户跟踪的股票信息
 */
@Entity
@Table(name = "track_stock", comment = "股票跟踪表 - 存储用户跟踪的股票信息")
public class TrackStockEntity {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名
     */
    @Column(name = "username", length = 50, comment = "用户名")
    private String username;

    /**
     * 股票代码
     */
    @Column(name = "ts_code", length = 20, comment = "股票代码")
    private String tsCode;

    /**
     * 股票名称
     */
    @Column(name = "stock_name", length = 50, comment = "股票名称")
    private String stockName;

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
     * 添加日期
     */
    @Column(name = "add_date", length = 8, comment = "添加日期")
    private String addDate;

    /**
     * 创建时间
     */
    @Column(name = "created_at", updatable = false, comment = "创建时间")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (addDate == null) {
            addDate = LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    @JsonProperty("username")
    public String getUsername() { return username; }
    @JsonProperty("username")
    public void setUsername(String username) { this.username = username; }

    @JsonProperty("ts_code")
    public String getTsCode() { return tsCode; }
    @JsonProperty("ts_code")
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }

    @JsonProperty("stock_name")
    public String getStockName() { return stockName; }
    @JsonProperty("stock_name")
    public void setStockName(String stockName) { this.stockName = stockName; }

    @JsonProperty("strategy_id")
    public Long getStrategyId() { return strategyId; }
    @JsonProperty("strategy_id")
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }

    @JsonProperty("strategy_name")
    public String getStrategyName() { return strategyName; }
    @JsonProperty("strategy_name")
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    @JsonProperty("add_date")
    public String getAddDate() { return addDate; }
    @JsonProperty("add_date")
    public void setAddDate(String addDate) { this.addDate = addDate; }

    @JsonProperty("created_at")
    public LocalDateTime getCreatedAt() { return createdAt; }
    @JsonProperty("created_at")
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}