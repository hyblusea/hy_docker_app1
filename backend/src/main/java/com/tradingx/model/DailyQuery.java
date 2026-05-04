package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DailyQuery {

    @JsonProperty("ts_code")
    private String tsCode;

    @JsonProperty("trade_date")
    private String tradeDate;

    @JsonProperty("start_date")
    private String startDate;

    @JsonProperty("end_date")
    private String endDate;
}
