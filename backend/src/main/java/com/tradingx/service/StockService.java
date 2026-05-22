package com.tradingx.service;

import com.tradingx.config.FtShareProperties;
import com.tradingx.model.*;
import com.tradingx.repository.StockListRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final FtShareProperties ftShareProperties;
    private final StockListRepository stockListRepository;
    private final KlineService klineService;
    private final RestClient restClient;

    public StockService(FtShareProperties ftShareProperties, StockListRepository stockListRepository,
                       KlineService klineService, RestClient restClient) {
        this.ftShareProperties = ftShareProperties;
        this.stockListRepository = stockListRepository;
        this.klineService = klineService;
        this.restClient = restClient;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneId.of("Asia/Shanghai"));

    @PostConstruct
    public void init() {
        long count = stockListRepository.count();
        if (count == 0) {
            log.info("股票列表为空，后台异步从 ft.tech 导入初始数据...");
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(3000);
                    refreshStockList();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        } else {
            log.info("股票列表已加载 {} 条记录", count);
            cleanupNullNameStocks();
        }
    }

    @Transactional
    public void cleanupNullNameStocks() {
        try {
            List<StockListEntity> all = stockListRepository.findAll();
            List<StockListEntity> invalid = all.stream()
                    .filter(e -> e.getStockName() == null || e.getStockName().isBlank())
                    .toList();
            if (!invalid.isEmpty()) {
                stockListRepository.deleteAll(invalid);
                log.info("已清理 {} 条无名称的股票记录", invalid.size());
            }
        } catch (Exception e) {
            log.error("清理无名称股票记录异常", e);
        }
    }

    // 此方法由 KlineSyncService.scheduledFullSync() 调用，不再单独作为定时任务
    public void scheduledRefreshStockList() {
        log.info("定时刷新股票列表: 开始");
        refreshStockList();
        log.info("定时刷新股票列表: 完成");
    }

    @Transactional
    public void refreshStockList() {
        try {
            Map<String, StockListEntity> stockMap = new LinkedHashMap<>();
            int page = 1;
            int pageSize = 200;
            int totalFetched = 0;

            while (true) {
                String url = String.format("%s/api/v1/stocks?page=%d&page_size=%d&style=detailed&order=gain-rate-desc&fields=[]&is_choice=false&filter_st=true",
                        ftShareProperties.getBaseUrl(), page, pageSize);

                String responseBody = restClient.get()
                        .uri(url)
                        .header("X-Client-Name", ftShareProperties.getClientName())
                        .retrieve()
                        .body(String.class);

                if (responseBody == null || responseBody.isBlank()) {
                    log.warn("ft.tech API 返回空响应, page={}", page);
                    break;
                }

                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode stocksNode = root.get("stocks");
                if (stocksNode == null || !stocksNode.isArray() || stocksNode.isEmpty()) {
                    log.warn("ft.tech API 返回无股票数据, page={}", page);
                    break;
                }

                for (JsonNode stock : stocksNode) {
                    StockListEntity entity = new StockListEntity();
                    String stockCode = getTextValue(stock, "stock_code");
                    if (stockCode == null || stockCode.isBlank()) {
                        stockCode = buildStockCode(stock);
                    }
                    if (stockCode == null) continue;

                    String stockName = getTextValue(stock, "stock_name");
                    if (stockName == null || stockName.isBlank()) {
                        stockName = getTextValue(stock, "symbol_name");
                    }
                    if (stockName == null || stockName.isBlank()) continue;

                    entity.setStockCode(stockCode);
                    entity.setStockName(stockName);
                    entity.setSymbolPinyin(getTextValue(stock, "symbol_pinyin"));
                    entity.setMarketValue(getBigDecimalValue(stock, "market_value"));
                    entity.setMarketValueCirculating(getBigDecimalValue(stock, "market_value_circulating"));
                    entity.setTotalShares(getBigDecimalValue(stock, "total_shares"));
                    entity.setCirculatingShares(getBigDecimalValue(stock, "circulating_shares"));
                    entity.setSectorName(getTextValue(stock, "sector_name"));
                    entity.setPeRatio(getBigDecimalValue(stock, "pe_ratio"));
                    stockMap.putIfAbsent(stockCode, entity);
                }

                totalFetched += stocksNode.size();
                int totalCount = root.has("count") ? root.get("count").asInt() : 0;
                if (totalCount > 0 && totalFetched >= totalCount) {
                    break;
                }
                page++;
            }

            List<StockListEntity> allStocks = new ArrayList<>(stockMap.values());
            if (!allStocks.isEmpty()) {
                stockListRepository.deleteAllInBatch();
                stockListRepository.saveAll(allStocks);
                stockListRepository.flush();
                log.info("股票列表刷新完成，共 {} 条", allStocks.size());
            } else {
                log.warn("股票列表刷新完成，但未获取到任何数据");
            }
        } catch (Exception e) {
            log.error("刷新股票列表失败", e);
        }
    }

    private String buildStockCode(JsonNode stock) {
        int symbolId = stock.has("symbol_id") ? stock.get("symbol_id").asInt() : 0;
        int exId = stock.has("ex_id") ? stock.get("ex_id").asInt() : 0;
        if (symbolId == 0) return null;
        String code = String.format("%06d", symbolId);
        String suffix = switch (exId) {
            case 3553 -> ".XSHG";
            case 3554 -> ".SZ";
            case 3555, 6217 -> ".BJ";
            default -> code.startsWith("6") ? ".XSHG" : code.startsWith("8") || code.startsWith("4") || code.startsWith("9") ? ".BJ" : ".SZ";
        };
        return code + suffix;
    }

    public List<StockBasic> getStockList() {
        List<StockListEntity> entities = stockListRepository.findAll();
        return entities.stream().map(this::toStockBasic).toList();
    }

    public List<StockBasic> getNonSTStockList() {
        List<StockListEntity> entities = stockListRepository.findNonSTStocks();
        return entities.stream().map(this::toStockBasic).toList();
    }

    public List<StockBasic> searchStock(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getStockList();
        }
        List<StockListEntity> entities = stockListRepository.searchByKeyword(keyword.trim());
        return entities.stream().map(this::toStockBasic).toList();
    }

    private StockBasic toStockBasic(StockListEntity entity) {
        StockBasic basic = new StockBasic();
        basic.setTsCode(entity.getStockCode());
        String symbol = entity.getStockCode() != null && entity.getStockCode().contains(".")
                ? entity.getStockCode().substring(0, entity.getStockCode().indexOf('.'))
                : entity.getStockCode();
        basic.setSymbol(symbol);
        basic.setName(entity.getStockName() != null ? entity.getStockName() : symbol);
        basic.setCnspell(entity.getSymbolPinyin());
        basic.setIndustry(entity.getSectorName() != null ? entity.getSectorName() : "");
        basic.setMarketValue(entity.getMarketValue());
        basic.setMarketValueCirculating(entity.getMarketValueCirculating());
        basic.setTotalShares(entity.getTotalShares());
        basic.setCirculatingShares(entity.getCirculatingShares());
        basic.setPeRatio(entity.getPeRatio());
        return basic;
    }

    public List<DailyQuote> queryDaily(DailyQuery query) {
        String period = query.getPeriod() != null ? query.getPeriod() : "day";
        String klinePeriod = switch (period) {
            case "week" -> "week";
            case "month" -> "month";
            default -> "day";
        };

        List<DailyQuote> localData = queryFromLocalDb(query.getTsCode(), klinePeriod, query.getStartDate(), query.getEndDate());
        if (!localData.isEmpty() && !query.isForceRefresh()) {
            return localData;
        }

        String span = switch (period) {
            case "week" -> "WEEK1";
            case "month" -> "MONTH1";
            default -> "DAY1";
        };

        try {
            String ftCode = toFtCode(query.getTsCode());
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(String.format("%s/api/v2/stocks/%s/ohlcs?span=%s&limit=5000",
                    ftShareProperties.getBaseUrl(), ftCode, span));

            if (query.getStartDate() != null && !query.getStartDate().isBlank()) {
                String startDateFormatted = formatDateForApi(query.getStartDate());
                urlBuilder.append("&start_date=").append(startDateFormatted);
            }
            if (query.getEndDate() != null && !query.getEndDate().isBlank()) {
                String endDateFormatted = formatDateForApi(query.getEndDate());
                urlBuilder.append("&end_date=").append(endDateFormatted);
            }

            String responseBody = restClient.get()
                    .uri(urlBuilder.toString())
                    .header("X-Client-Name", ftShareProperties.getClientName())
                    .retrieve()
                    .body(String.class);

            return mapFtToDailyQuotes(responseBody, query.getTsCode(), query.getStartDate(), query.getEndDate());
        } catch (Exception e) {
            log.error("ft.tech API 调用失败: tsCode={}, error={}", query.getTsCode(), e.getMessage());
            throw new RuntimeException("ft.tech API error: " + e.getMessage(), e);
        }
    }

    private List<DailyQuote> queryFromLocalDb(String tsCode, String period, String startDate, String endDate) {
        if (tsCode == null || tsCode.isBlank()) return Collections.emptyList();

        try {
            if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
                return klineService.getKlineData(tsCode, period, startDate, endDate);
            }
            return klineService.getKlineData(tsCode, period);
        } catch (Exception e) {
            log.warn("本地数据库查询失败: tsCode={}, period={}, error={}", tsCode, period, e.getMessage());
            return Collections.emptyList();
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

    private List<DailyQuote> mapFtToDailyQuotes(String responseBody, String tsCode, String startDate, String endDate) {
        if (responseBody == null || responseBody.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode ohlcsNode = root.get("ohlcs");
            if (ohlcsNode == null || !ohlcsNode.isArray()) {
                return Collections.emptyList();
            }

            BigDecimal prevClose = root.has("prev_close") && !root.get("prev_close").isNull()
                    ? new BigDecimal(root.get("prev_close").asText()) : null;

            List<DailyQuote> quotes = new ArrayList<>();
            for (JsonNode item : ohlcsNode) {
                String tradeDate = parseFtDate(item, "ctm");
                if (tradeDate == null) continue;

                if (startDate != null && !startDate.isBlank() && tradeDate.compareTo(startDate) < 0) continue;
                if (endDate != null && !endDate.isBlank() && tradeDate.compareTo(endDate) > 0) continue;

                DailyQuote quote = new DailyQuote();
                quote.setTsCode(tsCode);
                quote.setTradeDate(tradeDate);
                quote.setOpen(getBigDecimalValue(item, "o"));
                quote.setHigh(getBigDecimalValue(item, "h"));
                quote.setLow(getBigDecimalValue(item, "l"));
                quote.setClose(getBigDecimalValue(item, "c"));
                quote.setVol(getBigDecimalValue(item, "v"));
                quote.setAmount(getBigDecimalValue(item, "t"));
                quotes.add(quote);
            }

            quotes.sort(Comparator.comparing(DailyQuote::getTradeDate));

            if (!quotes.isEmpty() && prevClose != null) {
                quotes.get(0).setPreClose(prevClose);
            }
            for (int i = 1; i < quotes.size(); i++) {
                if (quotes.get(i).getPreClose() == null && quotes.get(i - 1).getClose() != null) {
                    quotes.get(i).setPreClose(quotes.get(i - 1).getClose());
                }
                DailyQuote q = quotes.get(i);
                DailyQuote prev = quotes.get(i - 1);
                if (q.getClose() != null && prev.getClose() != null && prev.getClose().compareTo(BigDecimal.ZERO) != 0) {
                    q.setPctChg(q.getClose().subtract(prev.getClose())
                            .divide(prev.getClose(), 4, BigDecimal.ROUND_HALF_UP)
                            .multiply(new BigDecimal("100")));
                    q.setChange(q.getClose().subtract(prev.getClose()));
                }
            }

            return quotes;
        } catch (Exception e) {
            log.error("解析 ft.tech 响应失败", e);
            return Collections.emptyList();
        }
    }

    private String parseFtDate(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || node.isNull()) return null;
        try {
            long ms = node.asLong();
            return DATE_FMT.format(Instant.ofEpochMilli(ms));
        } catch (Exception e) {
            return null;
        }
    }

    private String getTextValue(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    private BigDecimal getBigDecimalValue(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull()) return null;
        try {
            return new BigDecimal(f.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatDateForApi(String date) {
        // 将 yyyyMMdd 格式转换为 yyyy-MM-dd 格式
        if (date == null || date.length() != 8) return date;
        return date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);
    }
}
