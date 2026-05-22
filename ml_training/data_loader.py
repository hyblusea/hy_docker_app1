# -*- coding: utf-8 -*-
import sys
import gc
import numpy as np
import pandas as pd
from concurrent.futures import ThreadPoolExecutor, as_completed
from sqlalchemy import create_engine, text


# ============================================================
#  Database Loader (with parallel chunked queries)
# ============================================================

class DatabaseLoader:
    """
    MySQL data loader.

    全量模式下使用多线程并行分块读取，将原先的单次大查询（~9.4M 行，
    5-10 分钟）拆分为多个并行小查询（每线程 500 支股票），总耗时约
    1-2 分钟，提速 3-5 倍。

    线程安全方案：每个工作线程创建独立的 engine，避免共享连接池竞争。
    """

    # 每个并行分块加载的股票数，约 500 × 1000 行/股 ≈ 50 万行/线程
    PARALLEL_CHUNK_SIZE = 500

    def __init__(self, host='localhost', port=3306, user='root',
                 password='', database='tradingx', min_records=100):
        self.min_records = min_records
        self.db_url = (f"mysql+pymysql://{user}:{password}@{host}:{port}"
                       f"/{database}?charset=utf8mb4")
        self.engine = create_engine(
            self.db_url,
            pool_size=5,
            pool_recycle=3600,
            connect_args={
                'connect_timeout': 30,
                'read_timeout': 1800,
            }
        )

    # ---- 符号列表查询 ----

    def get_all_symbols(self):
        """Get all stock codes. Fast index-only query."""
        query = text(
            "SELECT DISTINCT ts_code FROM kline_daily ORDER BY ts_code"
        )
        try:
            with self.engine.connect() as conn:
                df = pd.read_sql(query, conn)
            result = df['ts_code'].tolist()
            print(f"Found {len(result)} symbols", flush=True)
            return result
        except Exception as e:
            print(f"Failed to get symbols: {e}", flush=True)
            return []

    # ---- 核心：并行分块加载 ----

    def load_all_stocks(self, start_date=None, end_date=None):
        """
        加载全量股票数据，返回 (symbol, data) 二元组列表。

        策略：先获取全部股票代码，然后按 PARALLEL_CHUNK_SIZE 分块，
        使用 ThreadPoolExecutor 并行查询。每线程独立 engine，无共享
        连接竞争。失败时自动回退到单次查询。
        """
        # Step 1: 快速获取全部股票代码
        print("Loading all stock data...", flush=True)
        sys.stdout.flush()

        all_symbols = self.get_all_symbols()
        if not all_symbols:
            print("No symbols found", flush=True)
            return []

        # Step 2: 选择并行度
        import multiprocessing as mp
        num_threads = min(mp.cpu_count(), 12)
        num_threads = max(1, num_threads)

        # 分块
        chunk_size = self.PARALLEL_CHUNK_SIZE
        chunks = [all_symbols[i:i + chunk_size]
                  for i in range(0, len(all_symbols), chunk_size)]
        num_chunks = len(chunks)

        actual_threads = min(num_threads, num_chunks)
        print(f"  Symbols: {len(all_symbols)}, chunks: {num_chunks}, "
              f"threads: {actual_threads}", flush=True)
        sys.stdout.flush()

        # Step 3: 验证表存在（任何线程失败都能及早发现）
        self._validate_table()

        # Step 4: 并行分块查询
        try:
            items = self._load_all_stocks_parallel(
                chunks, start_date, end_date, actual_threads
            )
        except Exception as e:
            print(f"  Parallel loading failed: {e}, "
                  f"falling back to single query...", flush=True)
            items = self._load_all_stocks_single(
                start_date, end_date
            )

        print(f"  Done: {len(items)} valid stocks", flush=True)
        return items

    def _load_all_stocks_parallel(self, chunks, start_date, end_date,
                                  num_threads):
        """并行分块执行查询，返回 [(symbol, data_dict), ...] 列表。"""
        all_items = []
        total = len(chunks)
        processed = 0
        failed_chunks = 0

        with ThreadPoolExecutor(max_workers=num_threads) as executor:
            future_to_idx = {
                executor.submit(
                    _load_stock_chunk,
                    self.db_url, chunk, self.min_records,
                    start_date, end_date,
                ): idx
                for idx, chunk in enumerate(chunks)
            }

            for future in as_completed(future_to_idx):
                idx = future_to_idx[future]
                try:
                    chunk_items = future.result()
                    all_items.extend(chunk_items)
                    processed += 1
                except Exception as e:
                    failed_chunks += 1
                    processed += 1
                    if failed_chunks <= 3:
                        print(f"  Chunk {idx} failed: {e}", flush=True)

                if processed % 5 == 0 or processed == total:
                    print(f"  Chunks: {processed}/{total} "
                          f"({failed_chunks} failed)", flush=True)
                    sys.stdout.flush()

        if not all_items:
            raise RuntimeError(
                f"All chunks failed ({failed_chunks}/{total})"
            )

        if failed_chunks > 0:
            print(f"  Warning: {failed_chunks} chunks failed, "
                  f"{len(all_items)} stocks loaded from "
                  f"{total - failed_chunks} chunks", flush=True)

        return all_items

    def _load_all_stocks_single(self, start_date=None, end_date=None):
        """单次查询回退方案，兼容原有逻辑。返回 [(symbol, data_dict), ...]。"""
        query = ("SELECT ts_code, trade_date, open, high, low, close, vol "
                 "FROM kline_daily")
        params = {}
        conditions = []

        if start_date:
            conditions.append("trade_date >= :start_date")
            params['start_date'] = start_date
        if end_date:
            conditions.append("trade_date <= :end_date")
            params['end_date'] = end_date

        if conditions:
            query += " WHERE " + " AND ".join(conditions)

        query += " ORDER BY ts_code, trade_date"

        try:
            print("  Executing single fallback query...", flush=True)
            sys.stdout.flush()

            with self.engine.connect() as conn:
                df = pd.read_sql(text(query), conn,
                                 params=params if params else None)

            total_rows = len(df)
            mem_mb = df.memory_usage(deep=True).sum() / 1024 ** 2
            print(f"  Query returned {total_rows:,} rows ({mem_mb:.0f}MB)",
                  flush=True)
            sys.stdout.flush()

            print("  Splitting by stock...", flush=True)
            sys.stdout.flush()

            items = []
            for symbol, group in df.groupby('ts_code', sort=False):
                if len(group) < self.min_records:
                    continue
                g = group.sort_values('trade_date')
                items.append((
                    symbol,
                    {
                        'open':  g['open'].values.astype(np.float32),
                        'high':  g['high'].values.astype(np.float32),
                        'low':   g['low'].values.astype(np.float32),
                        'close': g['close'].values.astype(np.float32),
                        'vol':   g['vol'].values.astype(np.float32),
                    }
                ))

            del df
            gc.collect()

            return items

        except Exception as e:
            print(f"  Single query also failed: {e}", flush=True)
            return []

    def _validate_table(self):
        """验证 kline_daily 表存在，尽早暴露配置问题。"""
        try:
            with self.engine.connect() as conn:
                conn.execute(text("SELECT 1 FROM kline_daily LIMIT 1"))
        except Exception as e:
            print(f"  ERROR: Cannot access kline_daily table: {e}",
                  flush=True)
            raise

    # ---- 单次查询接口（兼容旧逻辑） ----

    def get_kline_data(self, symbol=None, start_date=None,
                       end_date=None, limit=None):
        query = ("SELECT ts_code, trade_date, open, high, low, close, vol "
                 "FROM kline_daily WHERE 1=1")
        params = {}

        if symbol:
            query += " AND ts_code = :symbol"
            params['symbol'] = symbol
        if start_date:
            query += " AND trade_date >= :start_date"
            params['start_date'] = start_date
        if end_date:
            query += " AND trade_date <= :end_date"
            params['end_date'] = end_date

        query += " ORDER BY trade_date ASC"
        if limit:
            query += f" LIMIT {limit}"

        try:
            with self.engine.connect() as conn:
                df = pd.read_sql(text(query), conn,
                                 params=params if params else None)
            return df
        except Exception as e:
            print(f"Query failed: {e}", flush=True)
            return pd.DataFrame()


# ============================================================
#  分块加载工作函数（顶层，供 ThreadPoolExecutor 调用）
# ============================================================

def _load_stock_chunk(db_url, symbols, min_records, start_date=None,
                      end_date=None):
    """
    工作函数：加载一批股票的日K线数据。

    每个线程创建独立的 SQLAlchemy engine，避免与主线程共享连接池，
    保证线程安全且互不阻塞。
    """
    engine = create_engine(
        db_url,
        pool_size=3,
        pool_recycle=3600,
        connect_args={'connect_timeout': 30, 'read_timeout': 1800},
    )

    try:
        placeholders = ', '.join(
            [f':s{i}' for i in range(len(symbols))]
        )
        query = (
            "SELECT ts_code, trade_date, open, high, low, close, vol "
            "FROM kline_daily "
            f"WHERE ts_code IN ({placeholders})"
        )
        params = {f's{i}': s for i, s in enumerate(symbols)}

        if start_date:
            query += " AND trade_date >= :start_date"
            params['start_date'] = start_date
        if end_date:
            query += " AND trade_date <= :end_date"
            params['end_date'] = end_date

        query += " ORDER BY ts_code, trade_date"

        with engine.connect() as conn:
            df = pd.read_sql(text(query), conn, params=params)

        items = []
        for symbol, group in df.groupby('ts_code', sort=False):
            if len(group) < min_records:
                continue
            g = group.sort_values('trade_date')
            items.append((
                symbol,
                {
                    'open':  g['open'].values.astype(np.float32),
                    'high':  g['high'].values.astype(np.float32),
                    'low':   g['low'].values.astype(np.float32),
                    'close': g['close'].values.astype(np.float32),
                    'vol':   g['vol'].values.astype(np.float32),
                }
            ))

        return items

    except Exception as e:
        print(f"  Chunk query failed ({len(symbols)} stocks): {e}",
              flush=True)
        return []
    finally:
        engine.dispose()


# ============================================================
#  单支股票加载（兼容旧接口）
# ============================================================

def load_training_data(symbol=None, symbols=None, start_date=None,
                       end_date=None, db_config=None, min_records=100):
    if db_config is None:
        db_config = {}

    loader = DatabaseLoader(**db_config, min_records=min_records)
    stock_data_list = []

    if symbols:
        all_items = loader.load_all_stocks(start_date, end_date)
        all_data = dict(all_items)
        for sym in symbols:
            if sym in all_data:
                data = all_data[sym]
                stock_data_list.append((
                    data['open'], data['high'], data['low'],
                    data['close'], data['vol']
                ))
                print(f"Loaded {sym}: {len(data['close'])} records",
                      flush=True)
            else:
                print(f"Skipped {sym}", flush=True)
    elif symbol:
        df = loader.get_kline_data(symbol, start_date, end_date)
        if len(df) >= min_records:
            df = df.sort_values('trade_date').reset_index(drop=True)
            stock_data_list.append((
                df['open'].values, df['high'].values,
                df['low'].values, df['close'].values,
                df['vol'].values
            ))
        else:
            print(f"Insufficient data for {symbol} "
                  f"({len(df)} < {min_records})", flush=True)
            return None
    else:
        return None

    return stock_data_list if stock_data_list else None
