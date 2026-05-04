package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TushareResponse {

    @JsonProperty("request_id")
    private String requestId;

    private int code;

    private String msg;

    private TushareData data;

    @Data
    public static class TushareData {
        private List<String> fields;
        private List<List<Object>> items;
    }
}
