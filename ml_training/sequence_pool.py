# -*- coding: utf-8 -*-
import os
import sys
import gc
import struct
import numpy as np
import torch
from torch.utils.data import Dataset


# 统一的方向标签阈值（作为默认值），所有分类逻辑均引用此常量
DIRECTION_THRESHOLD = 0.005


def compute_direction_labels(magnitude, threshold=DIRECTION_THRESHOLD):
    directions = np.ones(len(magnitude), dtype=np.int64)
    directions[magnitude > threshold] = 2
    directions[magnitude < -threshold] = 0
    return directions


def _compute_direction_labels_chunked(y_mag_array, total_len,
                                       chunk_size=500000,
                                       threshold=DIRECTION_THRESHOLD):
    """
    分块计算方向标签，避免一次性加载整个 memmap 到内存。

    与 compute_direction_labels 逻辑一致，统一使用传入的 threshold。
    """
    y_dir = np.empty(total_len, dtype=np.int64)
    for start_idx in range(0, total_len, chunk_size):
        end_idx = min(start_idx + chunk_size, total_len)
        chunk = np.array(y_mag_array[start_idx:end_idx])
        labels = np.ones(end_idx - start_idx, dtype=np.int64)
        labels[chunk > threshold] = 2
        labels[chunk < -threshold] = 0
        y_dir[start_idx:end_idx] = labels
    return y_dir


class MemmapSequencePool:
    """
    Memmap-backed sequence pool.

    Windows 兼容策略：
    - 写入阶段使用普通二进制文件顺序写入（避免 memmap w+ 导致进程工作集暴涨）
    - 写入完成后以只读 memmap 重新打开
    """

    @classmethod
    def from_cache(cls, cache_dir, meta, registry,
                   dir_threshold=DIRECTION_THRESHOLD):
        """
        Reopen an existing memmap cache without rebuilding.
        Fast path for resume/retrain.
        """
        instance = cls.__new__(cls)
        instance.cache_dir = cache_dir
        instance.seq_length = meta['seq_length']
        instance.pred_horizon = meta['pred_horizon']
        instance.total_len = meta['total_seqs']
        instance.dir_threshold = dir_threshold

        x_path = os.path.join(cache_dir, 'X_seq.dat')
        y_path = os.path.join(cache_dir, 'y_mag.dat')
        sid_path = os.path.join(cache_dir, 'stock_ids.dat')

        instance.X = np.memmap(x_path, dtype='float32', mode='r',
                               shape=(meta['total_seqs'],
                                      meta['seq_length'], 37))
        instance.y_mag = np.memmap(y_path, dtype='float32', mode='r',
                                   shape=(meta['total_seqs'],))
        instance.stock_ids = np.memmap(sid_path, dtype='int64', mode='r',
                                       shape=(meta['total_seqs'],))

        # Rebuild stock_seq_map
        instance.stock_seq_map = {}
        raw_map = meta.get('stock_seq_map', {})
        for symbol, bounds in raw_map.items():
            instance.stock_seq_map[symbol] = (bounds[0], bounds[1])

        # Warn if threshold differs from cached value
        cached_threshold = meta.get('dir_threshold')
        if cached_threshold is not None and cached_threshold != dir_threshold:
            print(f"WARNING: dir_threshold changed from cached "
                  f"{cached_threshold} to {dir_threshold}. "
                  f"Direction labels will be recomputed.", flush=True)

        # Rebuild direction labels (fast, small) — 统一使用分块函数
        print("Computing direction labels from cache...", flush=True)
        instance.y_dir = _compute_direction_labels_chunked(
            instance.y_mag, meta['total_seqs'], threshold=dir_threshold
        )

        dist = np.bincount(instance.y_dir, minlength=3)
        print(f"Cache loaded: {meta['total_seqs']:,} sequences", flush=True)
        print(f"Direction: DOWN={dist[0]}, FLAT={dist[1]}, UP={dist[2]}",
              flush=True)

        return instance

    def __init__(self, all_features, all_closes, all_symbols,
                 stock_seq_map, registry, seq_length=60,
                 pred_horizon=5, cache_dir='./seq_cache',
                 dir_threshold=DIRECTION_THRESHOLD):
        self.seq_length = seq_length
        self.pred_horizon = pred_horizon
        self.cache_dir = cache_dir
        self.dir_threshold = dir_threshold
        os.makedirs(cache_dir, exist_ok=True)

        # ---- Count total sequences (fast pass, no allocation) ----
        total_seqs = 0
        for symbol, feat in all_features.items():
            n = len(feat) - seq_length - pred_horizon + 1
            if n > 0:
                total_seqs += n

        print(f"Total sequences: {total_seqs:,}", flush=True)
        x_bytes = total_seqs * seq_length * 37 * 4
        y_bytes = total_seqs * 4
        sid_bytes = total_seqs * 8
        total_gb = (x_bytes + y_bytes + sid_bytes) / 1024 ** 3
        print(f"Total disk size: {total_gb:.1f}GB", flush=True)

        x_path = os.path.join(cache_dir, 'X_seq.dat')
        y_path = os.path.join(cache_dir, 'y_mag.dat')
        sid_path = os.path.join(cache_dir, 'stock_ids.dat')

        # ---- Phase A: 顺序写入二进制文件（Windows 安全，无 memmap 工作集暴涨） ----
        print("Phase A: Writing sequences to disk via sequential I/O...",
              flush=True)

        x_fh = open(x_path, 'wb')
        y_fh = open(y_path, 'wb')
        sid_fh = open(sid_path, 'wb')

        idx = 0
        self.stock_seq_map = {}
        stocks_processed = 0
        t0 = __import__('time').time()

        for symbol in all_symbols:
            if symbol not in all_features:
                continue
            feat = all_features[symbol]
            closes = all_closes[symbol]
            n = len(feat) - seq_length - pred_horizon + 1
            if n <= 0:
                continue

            start = idx
            stock_x = np.zeros((n, seq_length, 37), dtype=np.float32)
            stock_y = np.zeros(n, dtype=np.float32)
            stock_sid = np.full(n, registry.get_stock_idx(symbol),
                                dtype=np.int64)

            for i in range(n):
                stock_x[i] = feat[i:i + seq_length]
                future_idx = i + seq_length + pred_horizon - 1
                curr_idx = i + seq_length - 1
                stock_y[i] = ((closes[future_idx] - closes[curr_idx])
                              / closes[curr_idx]
                              if closes[curr_idx] != 0 else 0)

            x_fh.write(stock_x.tobytes())
            y_fh.write(stock_y.tobytes())
            sid_fh.write(stock_sid.tobytes())

            del stock_x, stock_y, stock_sid
            idx += n

            self.stock_seq_map[symbol] = (start, idx)
            stocks_processed += 1

            if stocks_processed % 500 == 0:
                elapsed = __import__('time').time() - t0
                pct = idx / total_seqs * 100
                rate = idx / elapsed if elapsed > 0 else 0
                eta = (total_seqs - idx) / rate if rate > 0 else 0
                proc_mb = __import__('resource').getrusage(
                    __import__('resource').RUSAGE_SELF
                ).ru_maxrss / 1024 if sys.platform != 'win32' else 0
                print(f"  Sequences: {idx:,}/{total_seqs:,} "
                      f"({pct:.1f}%, {stocks_processed} stocks, "
                      f"{elapsed:.0f}s, ~{eta:.0f}s remaining)",
                      flush=True)
                sys.stdout.flush()

        x_fh.close()
        y_fh.close()
        sid_fh.close()

        elapsed = __import__('time').time() - t0
        actual = idx
        print(f"Phase A done: {actual:,} sequences in {elapsed:.1f}s",
              flush=True)

        # ---- 释放原始数据，立即回收内存 ----
        del all_features
        del all_closes
        gc.collect()

        # ---- Phase B: 以只读 memmap 重新打开 ----
        print("Phase B: Reopening as read-only memmap...", flush=True)

        self.X = np.memmap(x_path, dtype='float32', mode='r',
                           shape=(actual, seq_length, 37))
        self.y_mag = np.memmap(y_path, dtype='float32', mode='r',
                               shape=(actual,))
        self.stock_ids = np.memmap(sid_path, dtype='int64', mode='r',
                                   shape=(actual,))

        # Direction labels (small, keep in memory) — 统一使用分块函数
        print("Computing direction labels...", flush=True)
        self.y_dir = _compute_direction_labels_chunked(
            self.y_mag, actual, threshold=dir_threshold
        )

        self.total_len = actual
        dist = np.bincount(self.y_dir, minlength=3)
        print(f"Sequence pool ready: {actual:,} sequences", flush=True)
        print(f"Direction: DOWN={dist[0]}, FLAT={dist[1]}, UP={dist[2]}",
              flush=True)

    def __len__(self):
        return self.total_len


class NumpySequencePool:
    """In-memory sequence pool for single-stock or small-scale training."""

    def __init__(self, X, y_mag, stock_ids,
                 dir_threshold=DIRECTION_THRESHOLD):
        self.X = X.astype(np.float32)
        self.y_mag = y_mag.astype(np.float32)
        self.stock_ids = stock_ids.astype(np.int64)
        self.y_dir = compute_direction_labels(y_mag, threshold=dir_threshold)
        self.total_len = len(X)
        dist = np.bincount(self.y_dir, minlength=3)
        print(f"Sequence pool: {self.total_len:,} sequences", flush=True)
        print(f"Direction: DOWN={dist[0]}, FLAT={dist[1]}, UP={dist[2]}",
              flush=True)


class SequenceDataset(Dataset):
    """
    Index-based dataset that reads from memmap one item at a time.

    Key difference from old version:
    - Old: stored a COPY of all data (97GB in memory)  -> OOM
    - New: stores only index array (34MB) + reads from memmap per item

    Supports two modes:
    1. pool + indices: standard mode for MemmapSequencePool / NumpySequencePool
    2. Direct arrays:  backward-compatible mode for in-memory numpy arrays
    """

    def __init__(self, pool_or_x, indices_or_y_dir=None,
                 y_mag=None, stock_ids=None):
        """
        Mode 1 (preferred):
            pool_or_x:    MemmapSequencePool or NumpySequencePool
            indices_or_y_dir: np.ndarray of int64 indices into the pool

        Mode 2 (backward compatible, direct arrays):
            pool_or_x:    np.ndarray X features
            indices_or_y_dir: np.ndarray y_dir labels
            y_mag:        np.ndarray y_mag values
            stock_ids:    np.ndarray stock IDs
        """
        if y_mag is not None and stock_ids is not None:
            # Mode 2: direct array mode
            self._direct_mode = True
            self._X = pool_or_x
            self._y_dir = indices_or_y_dir
            self._y_mag = y_mag
            self._stock_ids = stock_ids
            self.pool = None
            self.indices = None
        else:
            # Mode 1: pool + indices mode
            self._direct_mode = False
            self.pool = pool_or_x
            self.indices = indices_or_y_dir

    def __len__(self):
        if self._direct_mode:
            return len(self._X)
        return len(self.indices)

    def __getitem__(self, idx):
        if self._direct_mode:
            x = torch.from_numpy(self._X[idx].copy())
            yd = torch.tensor(int(self._y_dir[idx]), dtype=torch.long)
            ym = torch.tensor(float(self._y_mag[idx]), dtype=torch.float)
            sid = torch.tensor(int(self._stock_ids[idx]), dtype=torch.long)
            return x, yd, ym, sid

        # Pool + indices mode: read ONE sequence from pool
        real_idx = int(self.indices[idx])
        x = torch.from_numpy(self.pool.X[real_idx].copy())
        yd = torch.tensor(int(self.pool.y_dir[real_idx]), dtype=torch.long)
        ym = torch.tensor(float(self.pool.y_mag[real_idx]), dtype=torch.float)
        sid = torch.tensor(int(self.pool.stock_ids[real_idx]), dtype=torch.long)
        return x, yd, ym, sid


def split_train_val(pool, val_ratio=0.15, stock_seq_map=None):
    """
    Split train/val by time, returning INDEX arrays (not masks).

    Returns index arrays instead of boolean masks because:
    - Boolean mask + memmap fancy indexing = copies everything into memory
    - Index array can be used with __getitem__ to read one-by-one
    """
    n = pool.total_len
    train_indices = []
    val_indices = []

    if stock_seq_map:
        for symbol, (start, end) in stock_seq_map.items():
            stock_n = end - start
            val_count = max(1, int(stock_n * val_ratio))
            train_indices.extend(range(start, end - val_count))
            val_indices.extend(range(end - val_count, end))
    else:
        val_count = max(1, int(n * val_ratio))
        train_indices = list(range(0, n - val_count))
        val_indices = list(range(n - val_count, n))

    return np.array(train_indices, dtype=np.int64), np.array(val_indices, dtype=np.int64)


class BalancedStockSampler:
    """
    Balanced stock sampler - index-based version.

    Receives: train indices array + reads stock_ids from pool.
    Yields batches of GLOBAL indices into the pool.
    """

    def __init__(self, indices, pool, batch_size=512, stocks_per_batch=128):
        self.batch_size = batch_size
        self.stocks_per_batch = stocks_per_batch
        self._total = len(indices)
        self.indices = indices

        # Build stock -> index mapping using pool's stock_ids
        # Store GLOBAL indices so yielded batches can be used
        # directly by SequenceDataset(pool, indices)[batch_idx]
        self.stock_to_indices = {}
        for pos_idx, global_idx in enumerate(indices):
            sid = int(pool.stock_ids[global_idx])
            if sid not in self.stock_to_indices:
                self.stock_to_indices[sid] = []
            self.stock_to_indices[sid].append(pos_idx)  # 存位置索引

        self.stock_list = list(self.stock_to_indices.keys())

        # ---- 防御性校验 ----
        if not self.stock_list:
            raise ValueError(
                "BalancedStockSampler: no stocks found in training indices. "
                "Check pool.stock_ids and train indices."
            )
        if self.stocks_per_batch <= 0:
            self.stocks_per_batch = min(self.batch_size, len(self.stock_list))
            print(f"WARNING: stocks_per_batch was 0, "
                  f"auto-corrected to {self.stocks_per_batch}", flush=True)

        self.num_batches = len(indices) // batch_size

    def __iter__(self):
        for _ in range(self.num_batches):
            selected = np.random.choice(
                self.stock_list,
                size=self.stocks_per_batch,
                replace=(self.stocks_per_batch > len(self.stock_list))
            )

            batch_indices = []
            per_stock = self.batch_size // self.stocks_per_batch

            for stock_id in selected:
                indices = self.stock_to_indices[stock_id]
                chosen = np.random.choice(
                    indices,
                    size=min(per_stock, len(indices)),
                    replace=(len(indices) < per_stock)
                )
                batch_indices.extend(chosen.tolist())

            while len(batch_indices) < self.batch_size:
                batch_indices.append(np.random.randint(0, self._total))

            yield batch_indices[:self.batch_size]

    def __len__(self):
        return self.num_batches
