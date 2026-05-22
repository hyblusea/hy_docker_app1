package com.tradingx.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tushare")
public class TushareProperties {

    private String apiUrl;
    private String token;
    private String stockBasicCsv;

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getStockBasicCsv() { return stockBasicCsv; }
    public void setStockBasicCsv(String stockBasicCsv) { this.stockBasicCsv = stockBasicCsv; }
}
