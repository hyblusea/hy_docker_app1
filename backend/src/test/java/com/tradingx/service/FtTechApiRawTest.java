package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

public class FtTechApiRawTest {

    private static final String FT_BASE_URL = "https://market.ft.tech/app";
    private static final String CLIENT_NAME = "ft-claw";

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void checkRawResponse() throws Exception {
        System.out.println("\n========== 检查 API 原始响应 ==========\n");
        
        String url = FT_BASE_URL + "/api/v1/stocks?page=1&page_size=10&style=detailed";
        
        System.out.println("请求 URL: " + url);
        
        String response = restClient.get()
                .uri(url)
                .header("X-Client-Name", CLIENT_NAME)
                .retrieve()
                .body(String.class);
        
        System.out.println("响应长度: " + response.length());
        System.out.println("\n完整响应:");
        
        JsonNode root = objectMapper.readTree(response);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }
}
