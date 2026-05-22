package com.tradingx.config;

import com.tradingx.client.DeepSeekWebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DeepSeekConfig {

    @Value("${deepseek.user-token:}")
    private String userToken;

    @Bean
    public DeepSeekWebClient deepSeekWebClient() {
        if (userToken == null || userToken.isBlank()) {
            return null;
        }
        DeepSeekWebClient client = new DeepSeekWebClient();
        client.setUserToken(userToken);
        return client;
    }
}
