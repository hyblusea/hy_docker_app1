package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

@Data
public class TushareRequest {

    @JsonProperty("api_name")
    private String apiName;

    private String token;

    private Map<String, String> params;

    private String fields;

    public static TushareRequest of(String apiName, String token, Map<String, String> params) {
        TushareRequest request = new TushareRequest();
        request.setApiName(apiName);
        request.setToken(token);
        request.setParams(params);
        request.setFields("");
        return request;
    }
}
