package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
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
}
