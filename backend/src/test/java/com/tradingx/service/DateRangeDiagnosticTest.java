package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateRangeDiagnosticTest {

    private static final String FT_BASE_URL = "https://market.ft.tech/app";
    private static final String CLIENT_NAME = "ft-claw";

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testDateRangeParameter() throws Exception {
        System.out.println("\n========== 测试时间范围参数效果 ==========\n");
        
        String code = "000001.SZ";
        
        // 测试不带时间范围
        System.out.println("1. 不带时间范围:");
        testRequest(String.format("%s/api/v2/stocks/%s/ohlcs?span=DAY1&limit=5000", FT_BASE_URL, code));
        
        // 测试带时间范围 (2020年开始)
        System.out.println("\n2. 带时间范围 (start_date=2020-01-01):");
        testRequest(String.format("%s/api/v2/stocks/%s/ohlcs?span=DAY1&limit=5000&start_date=2020-01-01", FT_BASE_URL, code));
        
        // 测试带时间范围 (2010年开始)
        System.out.println("\n3. 带时间范围 (start_date=2010-01-01):");
        testRequest(String.format("%s/api/v2/stocks/%s/ohlcs?span=DAY1&limit=5000&start_date=2010-01-01", FT_BASE_URL, code));
        
        // 测试带时间范围 (2000年开始)
        System.out.println("\n4. 带时间范围 (start_date=2000-01-01):");
        testRequest(String.format("%s/api/v2/stocks/%s/ohlcs?span=DAY1&limit=5000&start_date=2000-01-01", FT_BASE_URL, code));
    }

    private void testRequest(String url) throws Exception {
        String response = restClient.get()
                .uri(url)
                .header("X-Client-Name", CLIENT_NAME)
                .retrieve()
                .body(String.class);
        
        JsonNode root = objectMapper.readTree(response);
        JsonNode data = root.get("ohlcs");
        int count = data != null && data.isArray() ? data.size() : 0;
        
        if (count > 0 && data != null) {
            JsonNode first = data.get(0);
            JsonNode last = data.get(count - 1);
            
            String firstDate = parseDate(first);
            String lastDate = parseDate(last);
            
            System.out.printf("返回 %d 条数据\n", count);
            System.out.printf("最早日期: %s\n", firstDate);
            System.out.printf("最新日期: %s\n", lastDate);
        } else {
            System.out.println("无数据返回");
        }
    }

    private String parseDate(JsonNode item) {
        JsonNode node = item.get("otm");
        if (node == null || node.isNull()) return null;
        try {
            long ms = node.asLong();
            LocalDate date = LocalDate.ofEpochDay(ms / (24 * 60 * 60 * 1000));
            return date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (Exception e) {
            return null;
        }
    }
}
