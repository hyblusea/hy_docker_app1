package com.tradingx.config;

import com.tradingx.client.MimoWebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MimoConfig {

    @Value("${mimo.user-id:}")
    private String userId;

    @Value("${mimo.xiaomichatbot-ph:}")
    private String xiaomichatbotPh;

    @Value("${mimo.service-token:}")
    private String serviceToken;

    @Bean
    public MimoWebClient mimoWebClient() {
        if (userId == null || userId.isBlank() || 
            xiaomichatbotPh == null || xiaomichatbotPh.isBlank() ||
            serviceToken == null || serviceToken.isBlank()) {
            return null;
        }
        MimoWebClient client = new MimoWebClient();
        client.setCredentials(userId, xiaomichatbotPh, serviceToken);
        return client;
    }
}
