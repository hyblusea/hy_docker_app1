package com.tradingx.service;

import com.tradingx.model.*;
import com.tradingx.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class KlineService {

    private static final Logger log = LoggerFactory.getLogger(KlineService.class);

    private final KlineDailyRepository klineDailyRepository;
    private final KlineWeeklyRepository klineWeeklyRepository;
    private final KlineMonthlyRepository klineMonthlyRepository;
    private final DataSource dataSource;

    private static final String INSERT_DAILY_SQL =
            "INSERT INTO kline_daily (ts_code, trade_date, open, high, low, close, pre_close, change_val, pct_chg, vol, amount, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE open=VALUES(open), high=VALUES(high), low=VALUES(low), close=VALUES(close), pre_close=VALUES(pre_close), change_val=VALUES(change_val), pct_chg=VALUES(pct_chg), vol=VALUES(vol), amount=VALUES(amount)";
    private static final String INSERT_WEEKLY_SQL =
            "INSERT INTO kline_weekly (ts_code, trade_date, open, high, low, close, pre_close, change_val, pct_chg, vol, amount, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE open=VALUES(open), high=VALUES(high), low=VALUES(low), close=VALUES(close), pre_close=VALUES(pre_close), change_val=VALUES(change_val), pct_chg=VALUES(pct_chg), vol=VALUES(vol), amount=VALUES(amount)";
    private static final String INSERT_MONTHLY_SQL =
            "INSERT INTO kline_monthly (ts_code, trade_date, open, high, low, close, pre_close, change_val, pct_chg, vol, amount, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE open=VALUES(open), high=VALUES(high), low=VALUES(low), close=VALUES(close), pre_close=VALUES(pre_close), change_val=VALUES(change_val), pct_chg=VALUES(pct_chg), vol=VALUES(vol), amount=VALUES(amount)";

    public KlineService(KlineDailyRepository klineDailyRepository,
                       KlineWeeklyRepository klineWeeklyRepository,
                       KlineMonthlyRepository klineMonthlyRepository,
                       DataSource dataSource) {
        this.klineDailyRepository = klineDailyRepository;
        this.klineWeeklyRepository = klineWeeklyRepository;
        this.klineMonthlyRepository = klineMonthlyRepository;
        this.dataSource = dataSource;
    }

    private static final int MAX_RETRY = 5;
    private static final long RETRY_DELAY_MS = 500;

    public void saveKlineData(String tsCode, String period, List<DailyQuote> quotes) {
        if (quotes == null || quotes.isEmpty()) return;

        String sql = switch (period) {
            case "week" -> INSERT_WEEKLY_SQL;
            case "month" -> INSERT_MONTHLY_SQL;
            default -> INSERT_DAILY_SQL;
        };

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                executeBatchInsert(tsCode, period, sql, quotes);
                return;
            } catch (RuntimeException e) {
                if (isDeadlock(e) && attempt < MAX_RETRY) {
                    log.warn("K线数据插入遇到死锁, 重试 {}/{}: tsCode={}, period={}", attempt, MAX_RETRY, tsCode, period);
                    try { Thread.sleep(RETRY_DELAY_MS * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                } else {
                    throw e;
                }
            }
        }
    }

    private boolean isDeadlock(RuntimeException e) {
        Throwable cause = e.getCause();
        if (cause instanceof SQLException sqle) {
            return sqle.getErrorCode() == 1213 || "40001".equals(sqle.getSQLState());
        }
        return false;
    }

    private void executeBatchInsert(String tsCode, String period, String sql, List<DailyQuote> quotes) {
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED");
            }
            LocalDateTime now = LocalDateTime.now();
            Timestamp nowTs = Timestamp.valueOf(now);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int batchCount = 0;
                for (DailyQuote q : quotes) {
                    ps.setString(1, tsCode);
                    ps.setString(2, q.getTradeDate());
                    setBigDecimal(ps, 3, q.getOpen());
                    setBigDecimal(ps, 4, q.getHigh());
                    setBigDecimal(ps, 5, q.getLow());
                    setBigDecimal(ps, 6, q.getClose());
                    setBigDecimal(ps, 7, q.getPreClose());
                    setBigDecimal(ps, 8, q.getChange());
                    setBigDecimal(ps, 9, q.getPctChg());
                    setBigDecimal(ps, 10, q.getVol());
                    setBigDecimal(ps, 11, q.getAmount());
                    ps.setTimestamp(12, nowTs);
                    ps.addBatch();
                    batchCount++;

                    if (batchCount % 500 == 0) {
                        ps.executeBatch();
                        ps.clearBatch();
                        batchCount = 0;
                    }
                }

                if (batchCount > 0) {
                    ps.executeBatch();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ignored) {}
            }
            log.error("批量插入K线数据失败: tsCode={}, period={}, errorCode={}, sqlState={}, msg={}",
                    tsCode, period, e.getErrorCode(), e.getSQLState(), e.getMessage());
            throw new RuntimeException("批量插入K线数据失败: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    private void setBigDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value != null) {
            ps.setBigDecimal(index, value);
        } else {
            ps.setNull(index, Types.DECIMAL);
        }
    }

    public List<DailyQuote> getKlineData(String tsCode, String period) {
        List<DailyQuote> result = new ArrayList<>();
        switch (period) {
            case "day" -> {
                List<KlineDailyEntity> entities = klineDailyRepository.findByTsCodeOrderByTradeDateAsc(tsCode);
                for (KlineDailyEntity e : entities) {
                    result.add(toDailyQuote(e));
                }
            }
            case "week" -> {
                List<KlineWeeklyEntity> entities = klineWeeklyRepository.findByTsCodeOrderByTradeDateAsc(tsCode);
                for (KlineWeeklyEntity e : entities) {
                    result.add(toDailyQuote(e));
                }
            }
            case "month" -> {
                List<KlineMonthlyEntity> entities = klineMonthlyRepository.findByTsCodeOrderByTradeDateAsc(tsCode);
                for (KlineMonthlyEntity e : entities) {
                    result.add(toDailyQuote(e));
                }
            }
        }
        return result;
    }

    public List<DailyQuote> getKlineData(String tsCode, String period, String startDate, String endDate) {
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            return getKlineData(tsCode, period);
        }
        List<DailyQuote> result = new ArrayList<>();
        switch (period) {
            case "day" -> {
                List<KlineDailyEntity> entities = klineDailyRepository.findByTsCodeAndTradeDateBetweenOrderByTradeDateAsc(tsCode, startDate, endDate);
                for (KlineDailyEntity e : entities) {
                    result.add(toDailyQuote(e));
                }
            }
            case "week" -> {
                List<KlineWeeklyEntity> entities = klineWeeklyRepository.findByTsCodeAndTradeDateBetweenOrderByTradeDateAsc(tsCode, startDate, endDate);
                for (KlineWeeklyEntity e : entities) {
                    result.add(toDailyQuote(e));
                }
            }
            case "month" -> {
                List<KlineMonthlyEntity> entities = klineMonthlyRepository.findByTsCodeAndTradeDateBetweenOrderByTradeDateAsc(tsCode, startDate, endDate);
                for (KlineMonthlyEntity e : entities) {
                    result.add(toDailyQuote(e));
                }
            }
        }
        return result;
    }

    public String getFirstSyncDate(String tsCode, String period) {
        return switch (period) {
            case "day" -> klineDailyRepository.findFirstSyncDate(tsCode);
            case "week" -> klineWeeklyRepository.findFirstSyncDate(tsCode);
            case "month" -> klineMonthlyRepository.findFirstSyncDate(tsCode);
            default -> null;
        };
    }

    public String getLastSyncDate(String tsCode, String period) {
        return switch (period) {
            case "day" -> klineDailyRepository.findLastSyncDate(tsCode);
            case "week" -> klineWeeklyRepository.findLastSyncDate(tsCode);
            case "month" -> klineMonthlyRepository.findLastSyncDate(tsCode);
            default -> null;
        };
    }

    public long countRecords(String period) {
        String tableName = switch (period) {
            case "day" -> "kline_daily";
            case "week" -> "kline_weekly";
            case "month" -> "kline_monthly";
            default -> null;
        };
        if (tableName == null) return 0L;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.warn("获取近似行数失败: table={}, error={}", tableName, e.getMessage());
        }
        return 0L;
    }

    public int countStocks(String period) {
        return switch (period) {
            case "day" -> klineDailyRepository.countDistinctStocks();
            case "week" -> klineWeeklyRepository.countDistinctStocks();
            case "month" -> klineMonthlyRepository.countDistinctStocks();
            default -> 0;
        };
    }

    public String getGlobalStartDate(String period) {
        return switch (period) {
            case "day" -> klineDailyRepository.findGlobalStartDate();
            case "week" -> klineWeeklyRepository.findGlobalStartDate();
            case "month" -> klineMonthlyRepository.findGlobalStartDate();
            default -> null;
        };
    }

    public String getGlobalEndDate(String period) {
        return switch (period) {
            case "day" -> klineDailyRepository.findGlobalEndDate();
            case "week" -> klineWeeklyRepository.findGlobalEndDate();
            case "month" -> klineMonthlyRepository.findGlobalEndDate();
            default -> null;
        };
    }

    public void deleteKlineDataByTsCode(String tsCode, String period) {
        switch (period) {
            case "day" -> klineDailyRepository.deleteByTsCode(tsCode);
            case "week" -> klineWeeklyRepository.deleteByTsCode(tsCode);
            case "month" -> klineMonthlyRepository.deleteByTsCode(tsCode);
        }
    }

    public void deleteAllKlineData(String period) {
        switch (period) {
            case "day" -> klineDailyRepository.deleteAllBy();
            case "week" -> klineWeeklyRepository.deleteAllBy();
            case "month" -> klineMonthlyRepository.deleteAllBy();
        }
    }

    public void truncateKlineTable(String period) {
        String tableName = switch (period) {
            case "day" -> "kline_daily";
            case "week" -> "kline_weekly";
            case "month" -> "kline_monthly";
            default -> null;
        };
        if (tableName == null) return;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE " + tableName);
            log.info("TRUNCATE TABLE {} 完成", tableName);
        } catch (SQLException e) {
            log.error("TRUNCATE TABLE 失败: table={}, error={}", tableName, e.getMessage());
            throw new RuntimeException("TRUNCATE TABLE 失败: " + e.getMessage(), e);
        }
    }

    private DailyQuote toDailyQuote(KlineDailyEntity e) {
        DailyQuote q = new DailyQuote();
        q.setTsCode(e.getTsCode());
        q.setTradeDate(e.getTradeDate());
        q.setOpen(e.getOpen());
        q.setHigh(e.getHigh());
        q.setLow(e.getLow());
        q.setClose(e.getClose());
        q.setPreClose(e.getPreClose());
        q.setChange(e.getChange());
        q.setPctChg(e.getPctChg());
        q.setVol(e.getVol());
        q.setAmount(e.getAmount());
        return q;
    }

    private DailyQuote toDailyQuote(KlineWeeklyEntity e) {
        DailyQuote q = new DailyQuote();
        q.setTsCode(e.getTsCode());
        q.setTradeDate(e.getTradeDate());
        q.setOpen(e.getOpen());
        q.setHigh(e.getHigh());
        q.setLow(e.getLow());
        q.setClose(e.getClose());
        q.setPreClose(e.getPreClose());
        q.setChange(e.getChange());
        q.setPctChg(e.getPctChg());
        q.setVol(e.getVol());
        q.setAmount(e.getAmount());
        return q;
    }

    private DailyQuote toDailyQuote(KlineMonthlyEntity e) {
        DailyQuote q = new DailyQuote();
        q.setTsCode(e.getTsCode());
        q.setTradeDate(e.getTradeDate());
        q.setOpen(e.getOpen());
        q.setHigh(e.getHigh());
        q.setLow(e.getLow());
        q.setClose(e.getClose());
        q.setPreClose(e.getPreClose());
        q.setChange(e.getChange());
        q.setPctChg(e.getPctChg());
        q.setVol(e.getVol());
        q.setAmount(e.getAmount());
        return q;
    }
}
