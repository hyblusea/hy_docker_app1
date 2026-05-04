package com.tradingx.service;

import com.tradingx.config.TushareProperties;
import com.tradingx.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final TushareProperties tushareProperties;
    private final ResourceLoader resourceLoader;
    private final RestClient restClient;

    private List<StockBasic> stockBasicCache;

    public List<StockBasic> getStockList() {
        if (stockBasicCache != null) {
            return stockBasicCache;
        }
        stockBasicCache = loadStockBasicFromCsv();
        return stockBasicCache;
    }

    public List<StockBasic> searchStock(String keyword) {
        List<StockBasic> all = getStockList();
        if (keyword == null || keyword.isBlank()) {
            return all;
        }
        String kw = keyword.trim().toUpperCase();
        return all.stream()
                .filter(s -> s.getTsCode().toUpperCase().contains(kw)
                        || s.getName().contains(keyword.trim())
                        || s.getSymbol().contains(kw)
                        || (s.getCnspell() != null && s.getCnspell().toUpperCase().contains(kw)))
                .toList();
    }

    public List<DailyQuote> queryDaily(DailyQuery query) {
        Map<String, String> params = new LinkedHashMap<>();
        if (query.getTsCode() != null && !query.getTsCode().isBlank()) {
            params.put("ts_code", query.getTsCode().trim());
        }
        if (query.getTradeDate() != null && !query.getTradeDate().isBlank()) {
            params.put("trade_date", query.getTradeDate().trim());
        }
        if (query.getStartDate() != null && !query.getStartDate().isBlank()) {
            params.put("start_date", query.getStartDate().trim());
        }
        if (query.getEndDate() != null && !query.getEndDate().isBlank()) {
            params.put("end_date", query.getEndDate().trim());
        }

        TushareRequest request = TushareRequest.of("daily", tushareProperties.getToken(), params);
        TushareResponse response = callTushareApi(request);

        if (response == null || response.getCode() != 0) {
            String msg = response != null ? response.getMsg() : "Tushare API call failed";
            log.error("Tushare API error: {}", msg);
            throw new RuntimeException("Tushare API error: " + msg);
        }

        return mapToDailyQuotes(response);
    }

    private TushareResponse callTushareApi(TushareRequest request) {
        try {
            return restClient.post()
                    .uri(tushareProperties.getApiUrl())
                    .header("Content-Type", "application/json")
                    .body(request)
                    .retrieve()
                    .body(TushareResponse.class);
        } catch (Exception e) {
            log.error("Failed to call Tushare API", e);
            throw new RuntimeException("Failed to call Tushare API: " + e.getMessage(), e);
        }
    }

    private List<DailyQuote> mapToDailyQuotes(TushareResponse response) {
        if (response.getData() == null
                || response.getData().getFields() == null
                || response.getData().getItems() == null) {
            return Collections.emptyList();
        }

        List<String> fields = response.getData().getFields();
        List<List<Object>> items = response.getData().getItems();

        Map<String, Integer> fieldIndex = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            fieldIndex.put(fields.get(i), i);
        }

        List<DailyQuote> quotes = new ArrayList<>(items.size());
        for (List<Object> item : items) {
            DailyQuote quote = new DailyQuote();
            quote.setTsCode(getStringValue(item, fieldIndex, "ts_code"));
            quote.setTradeDate(getStringValue(item, fieldIndex, "trade_date"));
            quote.setOpen(getBigDecimalValue(item, fieldIndex, "open"));
            quote.setHigh(getBigDecimalValue(item, fieldIndex, "high"));
            quote.setLow(getBigDecimalValue(item, fieldIndex, "low"));
            quote.setClose(getBigDecimalValue(item, fieldIndex, "close"));
            quote.setPreClose(getBigDecimalValue(item, fieldIndex, "pre_close"));
            quote.setChange(getBigDecimalValue(item, fieldIndex, "change"));
            quote.setPctChg(getBigDecimalValue(item, fieldIndex, "pct_chg"));
            quote.setVol(getBigDecimalValue(item, fieldIndex, "vol"));
            quote.setAmount(getBigDecimalValue(item, fieldIndex, "amount"));
            quotes.add(quote);
        }

        quotes.sort((a, b) -> {
            if (a.getTradeDate() == null || b.getTradeDate() == null) return 0;
            return b.getTradeDate().compareTo(a.getTradeDate());
        });

        return quotes;
    }

    private String getStringValue(List<Object> item, Map<String, Integer> fieldIndex, String field) {
        Integer idx = fieldIndex.get(field);
        if (idx == null || idx >= item.size() || item.get(idx) == null) {
            return null;
        }
        return item.get(idx).toString();
    }

    private BigDecimal getBigDecimalValue(List<Object> item, Map<String, Integer> fieldIndex, String field) {
        Integer idx = fieldIndex.get(field);
        if (idx == null || idx >= item.size() || item.get(idx) == null) {
            return null;
        }
        try {
            return new BigDecimal(item.get(idx).toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<StockBasic> loadStockBasicFromCsv() {
        try {
            String csvPath = tushareProperties.getStockBasicCsv();
            java.io.InputStream inputStream = null;

            java.io.File externalFile = new java.io.File(csvPath);
            if (externalFile.exists()) {
                log.info("Loading stock basic from external file: {}", externalFile.getAbsolutePath());
                inputStream = new java.io.FileInputStream(externalFile);
            }

            if (inputStream == null) {
                java.io.File relativeFile = new java.io.File("data/tushare_stock_basic.csv");
                if (relativeFile.exists()) {
                    log.info("Loading stock basic from relative file: {}", relativeFile.getAbsolutePath());
                    inputStream = new java.io.FileInputStream(relativeFile);
                }
            }

            if (inputStream == null) {
                Resource resource = resourceLoader.getResource(csvPath);
                if (resource.exists()) {
                    log.info("Loading stock basic from classpath: {}", csvPath);
                    inputStream = resource.getInputStream();
                }
            }

            if (inputStream == null) {
                throw new RuntimeException("未找到股票列表CSV文件，已尝试路径: " + csvPath + ", data/tushare_stock_basic.csv, classpath");
            }

            List<StockBasic> result = new ArrayList<>();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new InputStreamReader(inputStream, "UTF-8"))) {
                String headerLine = reader.readLine();
                if (headerLine == null) return result;
                if (headerLine.startsWith("\uFEFF")) {
                    headerLine = headerLine.substring(1);
                }
                String[] headers = parseCsvLine(headerLine);

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] values = parseCsvLine(line);
                    StockBasic stock = new StockBasic();
                    for (int i = 0; i < Math.min(headers.length, values.length); i++) {
                        setStockField(stock, headers[i], values[i]);
                    }
                    result.add(stock);
                }
            }

            log.info("Loaded {} stocks from CSV", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to load stock basic CSV", e);
            throw new RuntimeException("Failed to load stock basic CSV: " + e.getMessage(), e);
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }

    private void setStockField(StockBasic stock, String header, String value) {
        switch (header) {
            case "ts_code" -> stock.setTsCode(value);
            case "symbol" -> stock.setSymbol(value);
            case "name" -> stock.setName(value);
            case "area" -> stock.setArea(value);
            case "industry" -> stock.setIndustry(value);
            case "cnspell" -> stock.setCnspell(value);
            case "market" -> stock.setMarket(value);
            case "list_date" -> stock.setListDate(value);
            case "act_name" -> stock.setActName(value);
            case "act_ent_type" -> stock.setActEntType(value);
        }
    }
}
