package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 股票列表实体类
 * 存储股票基本信息
 */
@Entity
@Table(name = "stock_list", indexes = {
    @Index(name = "idx_stock_code", columnList = "stock_code", unique = true)
}, comment = "股票列表表 - 存储股票基本信息")
public class StockListEntity {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 股票代码
     */
    @Column(name = "stock_code", nullable = false, length = 20, comment = "股票代码")
    private String stockCode;

    /**
     * 股票名称
     */
    @Column(name = "stock_name", length = 100, comment = "股票名称")
    private String stockName;

    /**
     * 股票拼音简称
     */
    @Column(name = "symbol_pinyin", length = 50, comment = "股票拼音简称")
    private String symbolPinyin;

    /**
     * 所属行业
     */
    @Column(name = "sector_name", length = 100, comment = "所属行业")
    private String sectorName;

    /**
     * 总市值
     */
    @Column(name = "market_value", comment = "总市值")
    private BigDecimal marketValue;

    /**
     * 流通市值
     */
    @Column(name = "market_value_circulating", comment = "流通市值")
    private BigDecimal marketValueCirculating;

    /**
     * 总股本
     */
    @Column(name = "total_shares", comment = "总股本")
    private BigDecimal totalShares;

    /**
     * 流通股本
     */
    @Column(name = "circulating_shares", comment = "流通股本")
    private BigDecimal circulatingShares;

    /**
     * 市盈率
     */
    @Column(name = "pe_ratio", comment = "市盈率")
    private BigDecimal peRatio;

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
    
    @JsonProperty("stock_code")
    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }
    
    @JsonProperty("stock_name")
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    
    @JsonProperty("symbol_pinyin")
    public String getSymbolPinyin() { return symbolPinyin; }
    public void setSymbolPinyin(String symbolPinyin) { this.symbolPinyin = symbolPinyin; }
    
    @JsonProperty("sector_name")
    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }
    
    @JsonProperty("market_value")
    public BigDecimal getMarketValue() { return marketValue; }
    public void setMarketValue(BigDecimal marketValue) { this.marketValue = marketValue; }
    
    @JsonProperty("market_value_circulating")
    public BigDecimal getMarketValueCirculating() { return marketValueCirculating; }
    public void setMarketValueCirculating(BigDecimal marketValueCirculating) { this.marketValueCirculating = marketValueCirculating; }
    
    @JsonProperty("total_shares")
    public BigDecimal getTotalShares() { return totalShares; }
    public void setTotalShares(BigDecimal totalShares) { this.totalShares = totalShares; }
    
    @JsonProperty("circulating_shares")
    public BigDecimal getCirculatingShares() { return circulatingShares; }
    public void setCirculatingShares(BigDecimal circulatingShares) { this.circulatingShares = circulatingShares; }

    @JsonProperty("pe_ratio")
    public BigDecimal getPeRatio() { return peRatio; }
    public void setPeRatio(BigDecimal peRatio) { this.peRatio = peRatio; }
    
    @JsonProperty("created_at")
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}