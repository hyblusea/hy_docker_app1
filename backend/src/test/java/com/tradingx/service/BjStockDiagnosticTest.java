package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

public class BjStockDiagnosticTest {

    private static final String FT_BASE_URL = "https://market.ft.tech/app";
    private static final String CLIENT_NAME = "ft-claw";

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void checkBjStocks() throws Exception {
        System.out.println("\n========== 检查北交所股票 ==========\n");
        
        List<String> bjStocks = new ArrayList<>();
        int totalStocks = 0;
        
        // 获取多页数据
        for (int page = 1; page <= 60; page++) {
            String url = String.format("%s/api/v1/stocks?page=%d&page_size=100&style=detailed", FT_BASE_URL, page);
            
            String response = restClient.get()
                    .uri(url)
                    .header("X-Client-Name", CLIENT_NAME)
                    .retrieve()
                    .body(String.class);
            
            JsonNode root = objectMapper.readTree(response);
            JsonNode stocksNode = root.get("stocks");
            
            if (stocksNode == null || !stocksNode.isArray() || stocksNode.isEmpty()) {
                break;
            }
            
            for (JsonNode stock : stocksNode) {
                int symbolId = stock.has("symbol_id") ? stock.get("symbol_id").asInt() : 0;
                int exId = stock.has("ex_id") ? stock.get("ex_id").asInt() : 0;
                String name = stock.has("symbol_name") ? stock.get("symbol_name").asText() : "";
                
                if (symbolId == 0) continue;
                
                totalStocks++;
                String code = String.format("%06d", symbolId);
                
                // 检查北交所股票 (ex_id=3555) 或 9/8/4 开头的股票
                if (exId == 3555 || code.startsWith("9") || code.startsWith("8") || code.startsWith("4")) {
                    bjStocks.add(String.format("%s (%s) - ex_id=%d", code, name, exId));
                }
            }
        }
        
        System.out.println("总股票数: " + totalStocks);
        System.out.println("北交所股票数: " + bjStocks.size());
        
        if (!bjStocks.isEmpty()) {
            System.out.println("\n北交所股票列表:");
            for (String s : bjStocks) {
                System.out.println("  " + s);
            }
        } else {
            System.out.println("\n未找到北交所股票!");
        }
    }
}
