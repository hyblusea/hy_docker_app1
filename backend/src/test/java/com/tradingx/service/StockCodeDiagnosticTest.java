package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

public class StockCodeDiagnosticTest {

    private static final String FT_BASE_URL = "https://market.ft.tech/app";
    private static final String CLIENT_NAME = "ft-claw";

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void checkStockListFromFtTech() throws Exception {
        System.out.println("\n========== 检查 ft.tech 股票列表 ==========\n");
        
        Set<String> bjStocks = new HashSet<>();
        Set<String> szStocks = new HashSet<>();
        Set<String> shStocks = new HashSet<>();
        
        // 获取多页数据
        for (int page = 1; page <= 5; page++) {
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
                String suffix = switch (exId) {
                    case 3553 -> ".XSHG";
                    case 3554 -> ".SZ";
                    case 3555 -> ".BJ";
                    default -> "?";
                };
                
                String tsCode = code + suffix;
                
                if (code.startsWith("9") || code.startsWith("8") || code.startsWith("4")) {
                    bjStocks.add(tsCode + " (" + name + ")");
                } else if (code.startsWith("6")) {
                    shStocks.add(tsCode + " (" + name + ")");
                } else {
                    szStocks.add(tsCode + " (" + name + ")");
                }
            }
        }
        
        System.out.println("北交所股票 (9/8/4开头): " + bjStocks.size() + " 只");
        System.out.println("示例: " + bjStocks.stream().limit(5).toList());
        
        System.out.println("\n上海股票 (6开头): " + shStocks.size() + " 只");
        System.out.println("示例: " + shStocks.stream().limit(5).toList());
        
        System.out.println("\n深圳股票 (0/3开头): " + szStocks.size() + " 只");
        System.out.println("示例: " + szStocks.stream().limit(5).toList());
    }

    @Test
    public void testSpecificStocks() throws Exception {
        System.out.println("\n========== 测试特定股票代码 ==========\n");
        
        String[] testCodes = {
            "920000.BJ",  // 北交所
            "920001.BJ",
            "430001.BJ",
            "830001.BJ",
            "000001.SZ",  // 深圳
            "600000.XSHG" // 上海
        };
        
        for (String code : testCodes) {
            String url = String.format("%s/api/v2/stocks/%s/ohlcs?span=DAY1&limit=3", FT_BASE_URL, code);
            
            try {
                String response = restClient.get()
                        .uri(url)
                        .header("X-Client-Name", CLIENT_NAME)
                        .retrieve()
                        .body(String.class);
                
                JsonNode root = objectMapper.readTree(response);
                JsonNode data = root.get("ohlcs");
                int count = data != null && data.isArray() ? data.size() : 0;
                
                System.out.printf("%s: %d 条数据 %s\n", code, count, count > 0 ? "✓" : "✗");
            } catch (Exception e) {
                System.out.printf("%s: 请求失败 - %s ✗\n", code, e.getMessage());
            }
        }
    }
}
