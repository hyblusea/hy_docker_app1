package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 选股K线引用实体类
 * 存储选股任务匹配结果的K线数据引用
 */
@Entity
@Table(name = "screen_quote", comment = "选股K线引用表 - 存储选股任务匹配结果的K线数据引用")
public class ScreenQuoteEntity {

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
     * 匹配结果ID
     */
    @Column(name = "match_id", comment = "匹配结果ID")
    private Long matchId;

    /**
     * 股票代码
     */
    @Column(name = "ts_code", length = 10, comment = "股票代码")
    private String tsCode;

    /**
     * 交易日期
     */
    @Column(name = "trade_date", length = 8, comment = "交易日期")
    private String tradeDate;

    /**
     * 开盘价
     */
    @Column(precision = 10, scale = 2, comment = "开盘价")
    private BigDecimal open;

    /**
     * 最高价
     */
    @Column(precision = 10, scale = 2, comment = "最高价")
    private BigDecimal high;

    /**
     * 最低价
     */
    @Column(precision = 10, scale = 2, comment = "最低价")
    private BigDecimal low;

    /**
     * 收盘价
     */
    @Column(precision = 10, scale = 2, comment = "收盘价")
    private BigDecimal close;

    /**
     * 前收盘价
     */
    @Column(name = "pre_close", precision = 10, scale = 2, comment = "前收盘价")
    private BigDecimal preClose;

    /**
     * 涨跌额
     */
    @Column(name = "change_val", precision = 10, scale = 2, comment = "涨跌额")
    private BigDecimal changeVal;

    /**
     * 涨跌幅(%)
     */
    @Column(name = "pct_chg", precision = 10, scale = 2, comment = "涨跌幅(%)")
    private BigDecimal pctChg;

    /**
     * 成交量(手)
     */
    @Column(precision = 20, scale = 2, comment = "成交量(手)")
    private BigDecimal vol;

    /**
     * 成交额(元)
     */
    @Column(precision = 20, scale = 2, comment = "成交额(元)")
    private BigDecimal amount;

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
    
    @JsonProperty("match_id")
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    
    @JsonProperty("ts_code")
    public String getTsCode() { return tsCode; }
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }
    
    @JsonProperty("trade_date")
    public String getTradeDate() { return tradeDate; }
    public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
    
    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }
    
    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }
    
    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }
    
    public BigDecimal getClose() { return close; }
    public void setClose(BigDecimal close) { this.close = close; }
    
    @JsonProperty("pre_close")
    public BigDecimal getPreClose() { return preClose; }
    public void setPreClose(BigDecimal preClose) { this.preClose = preClose; }
    
    @JsonProperty("change_val")
    public BigDecimal getChangeVal() { return changeVal; }
    public void setChangeVal(BigDecimal changeVal) { this.changeVal = changeVal; }
    
    @JsonProperty("pct_chg")
    public BigDecimal getPctChg() { return pctChg; }
    public void setPctChg(BigDecimal pctChg) { this.pctChg = pctChg; }
    
    public BigDecimal getVol() { return vol; }
    public void setVol(BigDecimal vol) { this.vol = vol; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    @JsonProperty("created_at")
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}