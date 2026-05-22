package com.tradingx.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class KlineSyncTest {

    private static final String FT_BASE_URL = "https://market.ft.tech";
    private static final String CLIENT_NAME = "ft-claw";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient = RestClient.create();

    @Test
    public void testFtTechApiRawResponse() {
        System.out.println("\n========== 测试 API 原始响应 ==========");
        
        String[] testUrls = {
            "https://market.ft.tech/app/api/v2/stocks/000001.SZ/ohlcs?span=DAY1&limit=5",
            "https://market.ft.tech/app/api/v2/stocks/920000.BJ/ohlcs?span=DAY1&limit=5",
            "https://market.ft.tech/app/api/v2/stocks/000001.SZ/ohlcs?span=DAY1&limit=5&start_date=2020-01-01&end_date=2026-05-12"
        };

        for (String url : testUrls) {
            System.out.println("\n请求 URL: " + url);
            try {
                String response = restClient.get()
                        .uri(url)
                        .header("X-Client-Name", CLIENT_NAME)
                        .retrieve()
                        .body(String.class);
                
                System.out.println("响应长度: " + response.length());
                System.out.println("响应内容 (前500字符):");
                System.out.println(response.substring(0, Math.min(500, response.length())));
            } catch (Exception e) {
                System.out.println("请求失败: " + e.getMessage());
            }
        }
    }

    @Test
    public void testStockCodeConversion() {
        System.out.println("\n========== 测试股票代码转换 ==========");
        
        Map<String, String> testCases = Map.of(
            "000001.SZ", "000001.SZ",
            "600000.XSHG", "600000.XSHG",
            "300001.SZ", "300001.SZ",
            "688001.XSHG", "688001.XSHG",
            "430001.BJ", "430001.BJ",
            "830001.BJ", "830001.BJ",
            "920001.BJ", "920001.BJ"
        );

        for (Map.Entry<String, String> entry : testCases.entrySet()) {
            String input = entry.getKey();
            String expected = entry.getValue();
            String actual = toFtCode(input);
            boolean match = expected.equals(actual);
            System.out.printf("输入: %s, 期望: %s, 实际: %s %s\n", 
                input, expected, actual, match ? "✓" : "✗");
        }
    }

    @Test
    public void testDefaultDataYears() {
        System.out.println("\n========== 测试默认数据年限 ==========");
        
        Map<String, Integer> defaultDataYears = Map.of(
            "day", 6,
            "week", 20,
            "month", 20
        );

        LocalDate today = LocalDate.now();
        
        for (Map.Entry<String, Integer> entry : defaultDataYears.entrySet()) {
            String period = entry.getKey();
            int years = entry.getValue();
            LocalDate startDate = today.minusYears(years);
            
            System.out.printf("%s: %d年, 起始日期=%s, 结束日期=%s\n",
                period, years, startDate.format(DATE_FMT), today.format(DATE_FMT));
        }
    }

    private String toFtCode(String tsCode) {
        if (tsCode == null || tsCode.isEmpty()) return tsCode;
        if (tsCode.contains(".")) {
            String code = tsCode.split("\\.")[0];
            String suffix = tsCode.split("\\.")[1];
            if ("XSHG".equals(suffix)) {
                return code + ".XSHG";
            } else if ("XSHE".equals(suffix) || "SZ".equals(suffix)) {
                return code + ".SZ";
            } else if ("BJ".equals(suffix)) {
                return code + ".BJ";
            }
            return tsCode;
        }
        if (tsCode.startsWith("6")) {
            return tsCode + ".XSHG";
        } else if (tsCode.startsWith("8") || tsCode.startsWith("4") || tsCode.startsWith("9")) {
            return tsCode + ".BJ";
        }
        return tsCode + ".SZ";
    }
}
