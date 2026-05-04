package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BacktestRequest {

    @JsonProperty("strategy_id")
    private Long strategyId;

    private List<DailyQuote> quotes;
}
