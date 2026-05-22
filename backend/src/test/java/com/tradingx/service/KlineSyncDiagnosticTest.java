package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class KlineSyncDiagnosticTest {

    private static final String FT_BASE_URL = "https://market.ft.tech/app";
    private static final String CLIENT_NAME = "ft-claw";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testFullSyncSimulation() throws Exception {
        System.out.println("\n========== 模拟全量同步流程 ==========\n");
        
        String[] testStocks = {
            "000001.SZ",   // 平安银行
            "600000.XSHG", // 浦发银行
            "920000.BJ",   // 北交所股票
            "920001.BJ",   // 北交所股票
        };

        LocalDate today = LocalDate.now();
        LocalDate dayStart = today.minusYears(6);
        LocalDate weekStart = today.minusYears(20);
        LocalDate monthStart = today.minusYears(20);

        for (String tsCode : testStocks) {
            System.out.println("\n--- 股票: " + tsCode + " ---");
            
            String ftCode = toFtCode(tsCode);
            System.out.println("转换后的代码: " + ftCode);
            
            // 测试日线
            System.out.println("\n日线数据 (6年):");
            testSync(ftCode, "DAY1", dayStart.format(DATE_FMT), today.format(DATE_FMT));
            
            // 测试周线
            System.out.println("\n周线数据 (20年):");
            testSync(ftCode, "WEEK1", weekStart.format(DATE_FMT), today.format(DATE_FMT));
            
            // 测试月线
            System.out.println("\n月线数据 (20年):");
            testSync(ftCode, "MONTH1", monthStart.format(DATE_FMT), today.format(DATE_FMT));
        }
    }

    private void testSync(String ftCode, String span, String startDate, String endDate) throws Exception {
        String startDateFormatted = formatDateForApi(startDate);
        String endDateFormatted = formatDateForApi(endDate);
        
        String url = String.format("%s/api/v2/stocks/%s/ohlcs?span=%s&limit=5000&start_date=%s&end_date=%s",
                FT_BASE_URL, ftCode, span, startDateFormatted, endDateFormatted);
        
        System.out.println("请求 URL: " + url);
        
        try {
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
        } catch (Exception e) {
            System.out.println("请求失败: " + e.getMessage());
        }
    }

    private String parseDate(JsonNode item) {
        JsonNode node = item.get("otm");
        if (node == null || node.isNull()) return null;
        try {
            long ms = node.asLong();
            LocalDate date = LocalDate.ofEpochDay(ms / (24 * 60 * 60 * 1000));
            return date.format(DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private String toFtCode(String tsCode) {
        if (tsCode == null || tsCode.isBlank()) {
            return "";
        }
        if (tsCode.contains(".")) {
            String code = tsCode.substring(0, tsCode.indexOf('.'));
            String suffix = tsCode.substring(tsCode.indexOf('.') + 1);
            String ftSuffix = switch (suffix.toUpperCase()) {
                case "SH" -> ".XSHG";
                case "SZ" -> ".SZ";
                case "BJ" -> ".BJ";
                case "XSHG", "XSHE" -> "." + suffix.toUpperCase();
                default -> code.startsWith("6") ? ".XSHG" : ".SZ";
            };
            return code + ftSuffix;
        }
        return tsCode.startsWith("6") ? tsCode + ".XSHG" : tsCode + ".SZ";
    }

    private String formatDateForApi(String date) {
        if (date == null || date.length() != 8) return date;
        return date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);
    }
}
