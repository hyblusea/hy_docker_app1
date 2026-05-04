package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StockBasic {

    @JsonProperty("ts_code")
    private String tsCode;

    private String symbol;

    private String name;

    private String area;

    private String industry;

    private String cnspell;

    private String market;

    @JsonProperty("list_date")
    private String listDate;

    @JsonProperty("act_name")
    private String actName;

    @JsonProperty("act_ent_type")
    private String actEntType;
}
