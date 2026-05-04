package com.tradingx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "tushare")
public class TushareProperties {

    private String apiUrl;
    private String token;
    private String stockBasicCsv;
}
