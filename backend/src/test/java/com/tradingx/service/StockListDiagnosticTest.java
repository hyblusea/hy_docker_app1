package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

public class StockListDiagnosticTest {

    private static final String FT_BASE_URL = "https://market.ft.tech/app";
    private static final String CLIENT_NAME = "ft-claw";

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void checkStockListFormat() throws Exception {
        System.out.println("\n========== 检查股票列表格式 ==========\n");
        
        List<String[]> results = new ArrayList<>();
        
        // 获取多页数据
        for (int page = 1; page <= 10; page++) {
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
                String name = stock.has("name") ? stock.get("name").asText() : "";
                
                if (symbolId == 0) continue;
                
                String code = String.format("%06d", symbolId);
                
                // 只关注9开头的股票
                if (code.startsWith("9")) {
                    String suffix = switch (exId) {
                        case 3553 -> ".XSHG";
                        case 3554 -> ".SZ";
                        case 3555 -> ".BJ";
                        default -> ".UNKNOWN";
                    };
                    results.add(new String[]{code + suffix, name, String.valueOf(exId)});
                }
            }
        }
        
        System.out.println("9开头股票数量: " + results.size());
        System.out.println("\n格式示例:");
        for (String[] row : results) {
            System.out.printf("  %s (%s) - ex_id=%s\n", row[0], row[1], row[2]);
        }
    }
}
