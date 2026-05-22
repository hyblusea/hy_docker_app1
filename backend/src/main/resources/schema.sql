-- TradingX 数据库初始化脚本
-- 先在 MySQL 中创建数据库：
-- CREATE DATABASE tradingx CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE tradingx;

-- ============================================
-- 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL DEFAULT 'user',
    status VARCHAR(255) NOT NULL DEFAULT 'pending',
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 股票列表表
-- ============================================
CREATE TABLE IF NOT EXISTS stock_list (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(10) NOT NULL UNIQUE,
    stock_name VARCHAR(50),
    symbol_pinyin VARCHAR(20),
    sector_name VARCHAR(50),
    market_value DECIMAL(20,2),
    market_value_circulating DECIMAL(20,2),
    total_shares DECIMAL(20,2),
    circulating_shares DECIMAL(20,2),
    created_at DATETIME,
    INDEX idx_stock_code (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- K线同步状态表
-- ============================================
CREATE TABLE IF NOT EXISTS kline_sync_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ts_code VARCHAR(10),
    period VARCHAR(10),
    stock_name VARCHAR(100),
    last_sync_date VARCHAR(8),
    start_date VARCHAR(8),
    status VARCHAR(50),
    total_records INT,
    data_years INT,
    error_message TEXT,
    consecutive_failures INT DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME,
    UNIQUE KEY unique_ts_period (ts_code, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 日K线表（独立表）
-- ============================================
CREATE TABLE IF NOT EXISTS kline_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ts_code VARCHAR(10) NOT NULL,
    trade_date VARCHAR(8) NOT NULL,
    open DECIMAL(12,4),
    high DECIMAL(12,4),
    low DECIMAL(12,4),
    close DECIMAL(12,4),
    pre_close DECIMAL(12,4),
    change_val DECIMAL(12,4),
    pct_chg DECIMAL(12,4),
    vol DECIMAL(20,2),
    amount DECIMAL(20,2),
    created_at DATETIME,
    UNIQUE KEY idx_kline_daily_ts_date (ts_code, trade_date),
    INDEX idx_kline_daily_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 周K线表（独立表）
-- ============================================
CREATE TABLE IF NOT EXISTS kline_weekly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ts_code VARCHAR(10) NOT NULL,
    trade_date VARCHAR(8) NOT NULL,
    open DECIMAL(12,4),
    high DECIMAL(12,4),
    low DECIMAL(12,4),
    close DECIMAL(12,4),
    pre_close DECIMAL(12,4),
    change_val DECIMAL(12,4),
    pct_chg DECIMAL(12,4),
    vol DECIMAL(20,2),
    amount DECIMAL(20,2),
    created_at DATETIME,
    UNIQUE KEY idx_kline_weekly_ts_date (ts_code, trade_date),
    INDEX idx_kline_weekly_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 月K线表（独立表）
-- ============================================
CREATE TABLE IF NOT EXISTS kline_monthly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ts_code VARCHAR(10) NOT NULL,
    trade_date VARCHAR(8) NOT NULL,
    open DECIMAL(12,4),
    high DECIMAL(12,4),
    low DECIMAL(12,4),
    close DECIMAL(12,4),
    pre_close DECIMAL(12,4),
    change_val DECIMAL(12,4),
    pct_chg DECIMAL(12,4),
    vol DECIMAL(20,2),
    amount DECIMAL(20,2),
    created_at DATETIME,
    UNIQUE KEY idx_kline_monthly_ts_date (ts_code, trade_date),
    INDEX idx_kline_monthly_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 策略表
-- ============================================
CREATE TABLE IF NOT EXISTS strategy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    code TEXT NOT NULL,
    language VARCHAR(50) DEFAULT 'java',
    valid BOOLEAN DEFAULT TRUE,
    compile_error TEXT,
    created_by VARCHAR(255),
    created_by_role VARCHAR(255),
    is_public BOOLEAN DEFAULT FALSE,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 选股任务表
-- ============================================
CREATE TABLE IF NOT EXISTS screen_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(50),
    start_date VARCHAR(8),
    end_date VARCHAR(8),
    total_stocks INT,
    match_count INT,
    completed BOOLEAN DEFAULT FALSE,
    init_error TEXT,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 选股匹配表
-- ============================================
CREATE TABLE IF NOT EXISTS screen_match (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(255),
    ts_code VARCHAR(10),
    stock_name VARCHAR(50),
    strategy_id BIGINT,
    strategy_name VARCHAR(100),
    total_return DOUBLE,
    win_rate DOUBLE,
    trade_count INT,
    profit_loss DOUBLE,
    max_drawdown DOUBLE,
    open_position_count INT,
    initial_capital DOUBLE,
    final_capital DOUBLE,
    total_fees DOUBLE,
    signals_json TEXT,
    created_at DATETIME,
    INDEX idx_screen_task_id (task_id),
    UNIQUE KEY unique_task_stock_strategy (task_id, ts_code, strategy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 选股行情表
-- ============================================
CREATE TABLE IF NOT EXISTS screen_quote (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(255),
    match_id BIGINT,
    ts_code VARCHAR(10),
    trade_date VARCHAR(8),
    open DECIMAL(12,4),
    high DECIMAL(12,4),
    low DECIMAL(12,4),
    close DECIMAL(12,4),
    pre_close DECIMAL(12,4),
    change_val DECIMAL(12,4),
    pct_chg DECIMAL(12,4),
    vol DECIMAL(20,2),
    amount DECIMAL(20,2),
    created_at DATETIME,
    INDEX idx_screen_quote_task (task_id),
    INDEX idx_screen_quote_match (match_id),
    INDEX idx_screen_quote_task_tscode (task_id, ts_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 策略分析任务表
-- ============================================
CREATE TABLE IF NOT EXISTS analysis_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(50),
    start_date VARCHAR(8),
    end_date VARCHAR(8),
    strategy_count INT,
    total_stocks INT,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    init_error TEXT,
    results_json MEDIUMTEXT,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 因子评估任务表
-- ============================================
CREATE TABLE IF NOT EXISTS factor_eval_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(50),
    start_date VARCHAR(8),
    end_date VARCHAR(8),
    factor_names TEXT,
    forward_days INT,
    total_stocks INT,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    init_error TEXT,
    results_json LONGTEXT,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 跟踪股票表
-- ============================================
CREATE TABLE IF NOT EXISTS track_stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    ts_code VARCHAR(10) NOT NULL,
    stock_name VARCHAR(50),
    strategy_id BIGINT,
    strategy_name VARCHAR(100),
    add_date VARCHAR(8),
    created_at DATETIME,
    UNIQUE KEY idx_track_user_stock_strategy (username, ts_code, strategy_id),
    INDEX idx_track_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 完成！
-- ============================================
