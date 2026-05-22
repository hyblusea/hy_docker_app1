package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class DailyQuote {

    @JsonProperty("ts_code")
    private String tsCode;

    @JsonProperty("trade_date")
    private String tradeDate;

    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;

    @JsonProperty("pre_close")
    private BigDecimal preClose;

    private BigDecimal change;

    @JsonProperty("pct_chg")
    private BigDecimal pctChg;

    private BigDecimal vol;
    private BigDecimal amount;

    public String getTsCode() { return tsCode; }
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }
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
    public BigDecimal getPreClose() { return preClose; }
    public void setPreClose(BigDecimal preClose) { this.preClose = preClose; }
    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }
    public BigDecimal getPctChg() { return pctChg; }
    public void setPctChg(BigDecimal pctChg) { this.pctChg = pctChg; }
    public BigDecimal getVol() { return vol; }
    public void setVol(BigDecimal vol) { this.vol = vol; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
