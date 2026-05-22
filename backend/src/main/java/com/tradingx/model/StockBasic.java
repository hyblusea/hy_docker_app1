package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class StockBasic {
    private String tsCode;
    private String symbol;
    private String name;
    private String cnspell;
    private String industry;
    private String market;
    private String listStatus;
    private String sectorName;
    private BigDecimal marketValue;
    private BigDecimal marketValueCirculating;
    private BigDecimal totalShares;
    private BigDecimal circulatingShares;
    private BigDecimal peRatio;

    @JsonProperty("ts_code")
    public String getTsCode() { return tsCode; }
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }
    
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getCnspell() { return cnspell; }
    public void setCnspell(String cnspell) { this.cnspell = cnspell; }
    
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    
    @JsonProperty("list_status")
    public String getListStatus() { return listStatus; }
    public void setListStatus(String listStatus) { this.listStatus = listStatus; }
    
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
}
