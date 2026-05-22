package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

public class FtTechApiDiagnosticTest {

    private static final String FT_BASE_URL = "https://market.ft.tech/app";
    private static final String CLIENT_NAME = "ft-claw";

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void checkExchangeDistribution() throws Exception {
        System.out.println("\n========== 检查交易所分布 ==========\n");
        
        Map<Integer, Integer> exIdCount = new HashMap<>();
        Map<Integer, String> exIdName = Map.of(
            3553, "上海",
            3554, "深圳",
            3555, "北京"
        );
        
        int totalStocks = 0;
        int bjStocks = 0;
        
        // 获取多页数据
        for (int page = 1; page <= 20; page++) {
            String url = String.format("%s/api/v1/stocks?page=%d&page_size=100&style=detailed", FT_BASE_URL, page);
            
            String response = restClient.get()
                    .uri(url)
                    .header("X-Client-Name", CLIENT_NAME)
                    .retrieve()
                    .body(String.class);
            
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");
            
            if (data == null || !data.isArray() || data.isEmpty()) {
                break;
            }
            
            for (JsonNode stock : data) {
                int symbolId = stock.has("symbol_id") ? stock.get("symbol_id").asInt() : 0;
                int exId = stock.has("ex_id") ? stock.get("ex_id").asInt() : 0;
                
                if (symbolId == 0) continue;
                
                totalStocks++;
                exIdCount.merge(exId, 1, Integer::sum);
                
                String code = String.format("%06d", symbolId);
                if (code.startsWith("9") || code.startsWith("8") || code.startsWith("4")) {
                    bjStocks++;
                    if (bjStocks <= 10) {
                        String name = stock.has("name") ? stock.get("name").asText() : "";
                        System.out.printf("北交所股票: %s (%s) - ex_id=%d\n", code, name, exId);
                    }
                }
            }
        }
        
        System.out.println("\n总股票数: " + totalStocks);
        System.out.println("北交所股票数 (9/8/4开头): " + bjStocks);
        System.out.println("\n交易所分布:");
        exIdCount.forEach((exId, count) -> {
            String name = exIdName.getOrDefault(exId, "未知");
            System.out.printf("  %s (ex_id=%d): %d 只\n", name, exId, count);
        });
    }
}
