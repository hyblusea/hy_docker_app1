package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 周K线实体类
 * 存储股票周线级别的K线数据
 */
@Entity
@Table(name = "kline_weekly", indexes = {
    @Index(name = "idx_kline_weekly_ts_date", columnList = "ts_code, trade_date", unique = true),
    @Index(name = "idx_kline_weekly_date", columnList = "trade_date")
}, comment = "周K线表 - 存储股票周线级别的K线数据")
public class KlineWeeklyEntity {

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
     * 交易日期
     */
    @Column(name = "trade_date", nullable = false, length = 8, comment = "交易日期")
    private String tradeDate;

    /**
     * 开盘价
     */
    @Column(precision = 12, scale = 4, comment = "开盘价")
    private BigDecimal open;

    /**
     * 最高价
     */
    @Column(precision = 12, scale = 4, comment = "最高价")
    private BigDecimal high;

    /**
     * 最低价
     */
    @Column(precision = 12, scale = 4, comment = "最低价")
    private BigDecimal low;

    /**
     * 收盘价
     */
    @Column(precision = 12, scale = 4, comment = "收盘价")
    private BigDecimal close;

    /**
     * 前收盘价
     */
    @Column(name = "pre_close", precision = 12, scale = 4, comment = "前收盘价")
    private BigDecimal preClose;

    /**
     * 涨跌额
     */
    @Column(name = "change_val", precision = 12, scale = 4, comment = "涨跌额")
    private BigDecimal change;

    /**
     * 涨跌幅(%)
     */
    @Column(name = "pct_chg", precision = 12, scale = 4, comment = "涨跌幅(%)")
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

    @JsonProperty("change")
    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }

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