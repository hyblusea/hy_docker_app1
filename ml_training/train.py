# -*- coding: utf-8 -*-
import os
import sys
import gc
import json
import time
import argparse
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim
from torch.utils.data import DataLoader
from tqdm import tqdm
import multiprocessing as mp
from concurrent.futures import ProcessPoolExecutor, as_completed

from model import KlineTransformer
from features import extract_all_features
from data_loader import DatabaseLoader, load_training_data
from stock_registry import StockRegistry
from sequence_pool import (
    MemmapSequencePool, NumpySequencePool, SequenceDataset,
    split_train_val, BalancedStockSampler, compute_direction_labels
)


# ============================================================
#  Learning Rate Scheduler
# ============================================================

class WarmupCosineScheduler:
    def __init__(self, optimizer, warmup_steps, total_steps, min_lr=1e-6):
        self.optimizer = optimizer
        self.warmup_steps = warmup_steps
        self.total_steps = total_steps
        self.min_lr = min_lr
        self.base_lrs = [g['lr'] for g in optimizer.param_groups]
        self.current_step = 0

    def step(self):
        self.current_step += 1
        if self.current_step <= self.warmup_steps:
            scale = self.current_step / max(1, self.warmup_steps)
        else:
            progress = ((self.current_step - self.warmup_steps)
                        / max(1, self.total_steps - self.warmup_steps))
            scale = 0.5 * (1.0 + np.cos(np.pi * progress))
        for group, base_lr in zip(self.optimizer.param_groups, self.base_lrs):
            group['lr'] = max(self.min_lr, base_lr * scale)

    def get_lr(self):
        return self.optimizer.param_groups[0]['lr']


# ============================================================
#  Loss Functions
# ============================================================

class FocalLoss(nn.Module):
    def __init__(self, alpha=None, gamma=2.0):
        super().__init__()
        self.gamma = gamma
        self.alpha = alpha

    def forward(self, inputs, targets):
        ce = F.cross_entropy(inputs, targets, reduction='none',
                             weight=self.alpha)
        pt = torch.exp(-ce)
        return ((1 - pt) ** self.gamma * ce).mean()


class HuberLoss(nn.Module):
    def __init__(self, delta=1.0):
        super().__init__()
        self.delta = delta

    def forward(self, pred, target):
        error = pred - target
        abs_err = torch.abs(error)
        quad = torch.min(abs_err,
                         torch.tensor(self.delta, device=abs_err.device))
        linear = abs_err - quad
        return (0.5 * quad ** 2 + self.delta * linear).mean()


# ============================================================
#  Parallel Feature Computation
# ============================================================

def _compute_single_stock_features(args):
    """
    Worker function for parallel feature computation.

    Called in a separate process via ProcessPoolExecutor.
    Must be a top-level function (picklable).
    """
    symbol, data = args
    feat = extract_all_features(
        data['open'], data['high'], data['low'],
        data['close'], data['vol']
    )
    return symbol, feat, data['close']


def compute_features_parallel(raw_data, max_workers=None):
    """
    Parallel feature computation using ProcessPoolExecutor.

    Args:
        raw_data: dict, {symbol: {'open':..., 'high':..., 'low':...,
                                  'close':..., 'vol':...}}

    Why parallel:
      - Each stock is independent (no cross-stock windows)
      - Single-thread: ~34 min for 5200 stocks
      - 8 workers:    ~5 min  (6-7x speedup)
      - 12 workers:   ~3 min  (GIL-free, multi-process)

    Why not merge all stocks into one array:
      - RSI/MACD/KDJ/Bollinger/ATR/ADX all have lookback windows
      - Merging would pollute windows across stock boundaries
    """
    if max_workers is None:
        max_workers = min(mp.cpu_count() - 1, 30)
        max_workers = max(1, max_workers)

    items = list(raw_data.items())
    total = len(items)

    print(f"Computing features with {max_workers} workers "
          f"({total} stocks)...", flush=True)
    sys.stdout.flush()

    all_features = {}
    all_closes = {}
    processed = 0
    failed = 0
    t0 = time.time()

    with ProcessPoolExecutor(max_workers=max_workers) as pool:
        futures = {
            pool.submit(_compute_single_stock_features, item): item[0]
            for item in items
        }

        for future in as_completed(futures):
            symbol = futures[future]
            try:
                sym, feat, closes = future.result()
                all_features[sym] = feat
                all_closes[sym] = closes
                processed += 1
            except Exception as e:
                failed += 1
                if failed <= 10:
                    print(f"  FAIL {symbol}: {e}", flush=True)

            if processed % 500 == 0 and processed > 0:
                elapsed = time.time() - t0
                remaining = total - processed - failed
                eta = elapsed / processed * remaining
                print(f"  Features: {processed}/{total} "
                      f"({elapsed:.0f}s elapsed, ~{eta:.0f}s remaining, "
                      f"{failed} failed)", flush=True)
                sys.stdout.flush()

    elapsed = time.time() - t0
    print(f"Feature computation done: {processed} stocks "
          f"in {elapsed:.1f}s ({failed} failed)", flush=True)

    return all_features, all_closes


# ============================================================
#  Data Preparation
# ============================================================

def prepare_full_training_data(db_config, cache_dir, registry,
                               seq_length, pred_horizon, min_records,
                               feature_workers=None,
                               start_date=None, end_date=None,
                               dir_threshold=0.005):
    """
    Full A-share data preparation pipeline.

    If memmap cache exists and matches seq_length/pred_horizon, reuse it.
    Only Phase 4 (split) needs to rerun.
    """
    import glob
    import shutil

    # ---- Check for existing cache ----
    x_path = os.path.join(cache_dir, 'X_seq.dat')
    y_path = os.path.join(cache_dir, 'y_mag.dat')
    sid_path = os.path.join(cache_dir, 'stock_ids.dat')
    meta_path = os.path.join(cache_dir, 'pool_meta.json')

    if (os.path.exists(x_path) and os.path.exists(y_path)
            and os.path.exists(sid_path) and os.path.exists(meta_path)):

        with open(meta_path, 'r') as f:
            meta = json.load(f)

        if (meta.get('seq_length') == seq_length
            and meta.get('pred_horizon') == pred_horizon
            and meta.get('min_records') == min_records):   # 新增

            print("=" * 60, flush=True)
            print("FOUND EXISTING CACHE - skipping Phase 1-3", flush=True)
            print(f"  Sequences: {meta['total_seqs']:,}", flush=True)
            print(f"  Stocks: {meta['num_stocks']}", flush=True)
            print("=" * 60, flush=True)

            # Load registry
            registry_path = os.path.join(
                os.path.dirname(cache_dir), 'models', 'stock_registry.json'
            )
            if not os.path.exists(registry_path):
                registry_path = os.path.join(
                    os.path.dirname(cache_dir),
                    '../backend/models/stock_registry.json'
                )
            if os.path.exists(registry_path):
                registry.load(registry_path)

            # Reopen memmap in read-only mode
            seq_pool = MemmapSequencePool.from_cache(
                cache_dir, meta, registry,
                dir_threshold=dir_threshold
            )

            # ---- 验证缓存完整性 ----
            if not seq_pool.stock_seq_map:
                print("WARNING: Cache stock_seq_map is empty "
                      "(cache may be corrupted).", flush=True)
                print("Deleting cache and rebuilding from scratch...",
                      flush=True)
                del seq_pool
                gc.collect()
                shutil.rmtree(cache_dir, ignore_errors=True)
                os.makedirs(cache_dir, exist_ok=True)
                # 不 return，落入下方完整流水线重建
            else:
                # 缓存有效，跳到 Phase 4
                print("Phase 4/4: Splitting train/val...", flush=True)

                train_idx, val_idx = split_train_val(
                    seq_pool, val_ratio=0.15,
                    stock_seq_map=seq_pool.stock_seq_map
                )

                print(f"  Train: {len(train_idx):,}, "
                      f"Val: {len(val_idx):,}", flush=True)

                train_dataset = SequenceDataset(seq_pool, train_idx)
                val_dataset = SequenceDataset(seq_pool, val_idx)

                train_sampler = BalancedStockSampler(
                    train_idx, seq_pool,
                    batch_size=512,
                    stocks_per_batch=min(
                        128, len(seq_pool.stock_seq_map)
                    )
                )

                print(f"Phase 4/4 done", flush=True)

                return (seq_pool, train_dataset, val_dataset,
                        train_sampler)

        else:
            print(f"Cache mismatch: cache seq={meta.get('seq_length')} "
                  f"vs requested seq={seq_length}", flush=True)
            print("Will rebuild from scratch...", flush=True)

    # ---- No cache or mismatch: full pipeline ----
    loader = DatabaseLoader(**db_config, min_records=min_records)

    # Phase 1
    print("Phase 1/4: Loading raw data...", flush=True)
    t0 = time.time()
    raw_items = loader.load_all_stocks(start_date, end_date)
    if not raw_items:
        print("ERROR: No valid stock data", flush=True)
        return None
    print(f"Phase 1/4 done: {len(raw_items)} stocks "
          f"({time.time()-t0:.1f}s)", flush=True)

    # 【修复】load_all_stocks 返回 list[(symbol, dict), ...]，
    # 转为 dict 以匹配 compute_features_parallel 的接口要求
    raw_data = dict(raw_items)
    del raw_items
    gc.collect()

    # Phase 2
    all_symbols = list(raw_data.keys())
    registry.register_stocks(all_symbols)

    print("Phase 2/4: Computing features...", flush=True)
    t0 = time.time()

    all_features, all_closes = compute_features_parallel(
        raw_data, max_workers=feature_workers
    )

    del raw_data
    gc.collect()

    print(f"Phase 2/4 done ({time.time()-t0:.1f}s)", flush=True)

    # Phase 3
    print("Phase 3/4: Building memmap sequence pool...", flush=True)
    t0 = time.time()

    seq_pool = MemmapSequencePool(
        all_features, all_closes, all_symbols,
        stock_seq_map=None,
        registry=registry,
        seq_length=seq_length,
        pred_horizon=pred_horizon,
        cache_dir=cache_dir,
        dir_threshold=dir_threshold,
    )

    # ---- 保存前验证 stock_seq_map 非空 ----
    if not seq_pool.stock_seq_map:
        print("ERROR: stock_seq_map is empty after pool creation!",
              flush=True)
        return None

    # Save cache metadata for reuse
    meta = {
        'total_seqs': seq_pool.total_len,
        'seq_length': seq_length,
        'pred_horizon': pred_horizon,
        'dir_threshold': dir_threshold,
        'min_records': min_records,          # 新增
        'num_stocks': len(seq_pool.stock_seq_map),
        'stock_seq_map': {
            k: list(v) for k, v in seq_pool.stock_seq_map.items()
        },
    }
    with open(meta_path, 'w') as f:
        json.dump(meta, f)
    print(f"Cache metadata saved: {meta_path}", flush=True)

    del all_features
    del all_closes
    gc.collect()

    print(f"Phase 3/4 done ({time.time()-t0:.1f}s)", flush=True)

    # Phase 4
    print("Phase 4/4: Splitting train/val...", flush=True)

    train_idx, val_idx = split_train_val(
        seq_pool, val_ratio=0.15,
        stock_seq_map=seq_pool.stock_seq_map
    )

    print(f"  Train: {len(train_idx):,}, Val: {len(val_idx):,}", flush=True)

    train_dataset = SequenceDataset(seq_pool, train_idx)
    val_dataset = SequenceDataset(seq_pool, val_idx)

    train_sampler = BalancedStockSampler(
        train_idx, seq_pool,
        batch_size=512,
        stocks_per_batch=min(128, len(all_symbols))
    )

    print(f"Phase 4/4 done", flush=True)

    return seq_pool, train_dataset, val_dataset, train_sampler



def prepare_single_stock_data(symbol, db_config, seq_length,
                              pred_horizon, min_records,
                              start_date=None, end_date=None,
                              dir_threshold=0.005):
    """Single stock data preparation (backward compatible)."""
    data = load_training_data(
        symbol=symbol,
        start_date=start_date,
        end_date=end_date,
        db_config=db_config,
        min_records=min_records,
    )
    if data is None:
        return None

    opens, highs, lows, closes, volumes = data[0]
    feat = extract_all_features(opens, highs, lows, closes, volumes)

    n = len(feat)
    num_seqs = n - seq_length - pred_horizon + 1
    if num_seqs <= 0:
        print(f"Not enough data for sequences (need "
              f"{seq_length + pred_horizon}, have {n})", flush=True)
        return None

    X = np.zeros((num_seqs, seq_length, 37), dtype=np.float32)
    y_mag = np.zeros(num_seqs, dtype=np.float32)
    stock_ids = np.zeros(num_seqs, dtype=np.int64)

    for i in range(num_seqs):
        X[i] = feat[i:i + seq_length]
        future_idx = i + seq_length + pred_horizon - 1
        curr_idx = i + seq_length - 1
        y_mag[i] = ((closes[future_idx] - closes[curr_idx])
                    / closes[curr_idx] if closes[curr_idx] != 0 else 0)

    pool = NumpySequencePool(X, y_mag, stock_ids,
                             dir_threshold=dir_threshold)

    val_count = max(1, int(len(X) * 0.15))

    train_indices = np.arange(0, len(X) - val_count, dtype=np.int64)
    val_indices = np.arange(len(X) - val_count, len(X), dtype=np.int64)

    train_dataset = SequenceDataset(pool, train_indices)
    val_dataset = SequenceDataset(pool, val_indices)

    return pool, train_dataset, val_dataset, None


# ============================================================
#  Training Core
# ============================================================

def train_model(
    train_dataset,
    val_dataset,
    train_sampler=None,
    model=None,
    registry=None,
    epochs=100,
    batch_size=64,
    seq_length=60,
    lr=0.0005,
    dir_weight=1.0,
    mag_weight=1.0,
    grad_clip=1.0,
    warmup_ratio=0.1,
    patience=20,
    device='cpu',
    output_dir='../backend/models',
    use_embedding=False,
):
    print(f"Device: {device}", flush=True)
    os.makedirs(output_dir, exist_ok=True)

    # --- DataLoader ---
    if train_sampler is not None:
        train_loader = DataLoader(
            train_dataset,
            batch_sampler=train_sampler,
            num_workers=0
            pin_memory=False, 
        )

    else:
        train_loader = DataLoader(
            train_dataset,
            batch_size=batch_size,
            shuffle=True,
            num_workers=0,
            pin_memory=False,
        )

    val_loader = DataLoader(
        val_dataset,
        batch_size=batch_size,
        shuffle=False,
        num_workers=0,
        pin_memory=False,
    )

    # --- Model ---
    if model is None:
        num_stocks = registry.num_stocks if registry else 1
        num_sectors = registry.num_sectors if registry else 1

        model = KlineTransformer(
            input_dim=37,
            d_model=256,
            nhead=8,
            num_layers=6,
            dim_feedforward=1024,
            dropout=0.15,
            max_len=seq_length,
            use_stock_embedding=use_embedding,
            num_stocks=num_stocks,
            num_sectors=num_sectors,
            stock_emb_dim=32,
        ).to(device)

        total_params = sum(p.numel() for p in model.parameters())
        print(f"Model parameters: {total_params:,}", flush=True)

    # --- Class weights (batch index from pool, no per-item memmap I/O) ---
    if hasattr(train_dataset, 'indices') and hasattr(train_dataset, 'pool') \
            and train_dataset.pool is not None:
        sample_count = min(50001, len(train_dataset))
        sample_indices = train_dataset.indices[:sample_count]
        train_y_dir = np.array(
            train_dataset.pool.y_dir[sample_indices], dtype=np.int64
        )
    elif hasattr(train_dataset, '_direct_mode') and train_dataset._direct_mode:
        # Direct array mode
        sample_count = min(50001, len(train_dataset))
        train_y_dir = np.array(
            train_dataset._y_dir[:sample_count], dtype=np.int64
        )
    else:
        # Fallback: single-stock NumpySequencePool 模式
        train_y_dir = np.array(
            [train_dataset[i][1].item()
             for i in range(min(50001, len(train_dataset)))]
        )

    train_counts = np.bincount(train_y_dir, minlength=3).astype(float)
    class_weights = 1.0 / (train_counts + 1)
    class_weights = class_weights / class_weights.sum() * 3
    class_weights_tensor = torch.FloatTensor(class_weights).to(device)
    print(f"Class weights: {class_weights}", flush=True)

    # --- Loss and optimizer ---
    dir_criterion = FocalLoss(alpha=class_weights_tensor, gamma=2.0)
    mag_criterion = HuberLoss(delta=0.02)
    optimizer = optim.AdamW(model.parameters(), lr=lr, weight_decay=0.01)

    total_steps = epochs * len(train_loader)
    warmup_steps = int(total_steps * warmup_ratio)
    scheduler = WarmupCosineScheduler(
        optimizer, warmup_steps, total_steps, min_lr=lr * 0.01
    )

    # --- Training loop ---
    best_val_loss = float('inf')
    patience_counter = 0
    loss_history = []

    print("Starting training...", flush=True)

    for epoch in range(epochs):
        # ---- Train ----
        model.train()
        t_dir_loss = 0
        t_mag_loss = 0
        t_correct = 0
        t_total = 0

        for batch in tqdm(train_loader, desc=f"Epoch {epoch + 1}/{epochs}",
                          ascii=True, ncols=80):
            X_b, y_dir_b, y_mag_b, stock_b = [
                b.to(device) for b in batch
            ]

            if use_embedding and registry:
                sector_b = torch.tensor(
                    [registry.get_sector_idx(
                        registry.idx_to_symbol.get(s.item(), ''))
                     for s in stock_b],
                    dtype=torch.long,
                    device=device,
                )
            else:
                sector_b = None

            optimizer.zero_grad()
            dir_logits, mag_pred = model(X_b, stock_b, sector_b)

            d_loss = dir_criterion(dir_logits, y_dir_b)
            m_loss = mag_criterion(mag_pred, y_mag_b)
            total_loss = dir_weight * d_loss + mag_weight * m_loss

            total_loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), grad_clip)
            optimizer.step()
            scheduler.step()

            t_dir_loss += d_loss.item()
            t_mag_loss += m_loss.item()
            preds = dir_logits.argmax(dim=1)
            t_correct += (preds == y_dir_b).sum().item()
            t_total += len(y_dir_b)

            # Progress for Java caller
            overall = ((epoch + (t_total / len(train_dataset)))
                       / epochs * 100)
            print(f"PROGRESS:{overall:.1f}", flush=True)

        # ---- Validate ----
        model.eval()
        v_dir_loss = 0
        v_mag_loss = 0
        v_correct = 0
        v_total = 0

        with torch.no_grad():
            for batch in val_loader:
                X_b, y_dir_b, y_mag_b, stock_b = [
                    b.to(device) for b in batch
                ]

                if use_embedding and registry:
                    sector_b = torch.tensor(
                        [registry.get_sector_idx(
                            registry.idx_to_symbol.get(s.item(), ''))
                         for s in stock_b],
                        dtype=torch.long,
                        device=device,
                    )
                else:
                    sector_b = None

                dir_logits, mag_pred = model(X_b, stock_b, sector_b)

                v_dir_loss += dir_criterion(dir_logits, y_dir_b).item()
                v_mag_loss += mag_criterion(mag_pred, y_mag_b).item()
                preds = dir_logits.argmax(dim=1)
                v_correct += (preds == y_dir_b).sum().item()
                v_total += len(y_dir_b)

        avg_t_dir = t_dir_loss / max(len(train_loader), 1)
        avg_t_mag = t_mag_loss / max(len(train_loader), 1)
        t_acc = t_correct / t_total if t_total > 0 else 0

        avg_v_dir = v_dir_loss / max(len(val_loader), 1)
        avg_v_mag = v_mag_loss / max(len(val_loader), 1)
        v_acc = v_correct / v_total if v_total > 0 else 0
        v_total_loss = avg_v_dir + avg_v_mag

        current_lr = scheduler.get_lr()

        loss_history.append({
            'epoch': epoch + 1,
            'train_dir_loss': avg_t_dir,
            'train_mag_loss': avg_t_mag,
            'train_acc': t_acc,
            'val_dir_loss': avg_v_dir,
            'val_mag_loss': avg_v_mag,
            'val_acc': v_acc,
            'lr': current_lr,
        })

        print(f"Epoch {epoch + 1}/{epochs} - "
              f"Train: dir={avg_t_dir:.4f} mag={avg_t_mag:.6f} "
              f"acc={t_acc:.4f} | "
              f"Val: dir={avg_v_dir:.4f} mag={avg_v_mag:.6f} "
              f"acc={v_acc:.4f} | LR={current_lr:.2e}", flush=True)

        # ---- Save best model ----
        if v_total_loss < best_val_loss:
            best_val_loss = v_total_loss
            patience_counter = 0
            torch.save(model.state_dict(),
                       os.path.join(output_dir, 'best_model.pth'))
        else:
            patience_counter += 1
            if patience_counter >= patience:
                print(f"Early stopping at epoch {epoch + 1}", flush=True)
                break

    # ---- Done ----
    print("PROGRESS:100.0", flush=True)
    print(f"Training complete. Best val loss: {best_val_loss:.6f}",
          flush=True)

    model.load_state_dict(
        torch.load(os.path.join(output_dir, 'best_model.pth'),
                   weights_only=True)
    )

    with open(os.path.join(output_dir, 'loss_history.json'), 'w') as f:
        json.dump(loss_history, f, indent=2)

    model.export_torchscript(
        seq_length,
        os.path.join(output_dir, 'prediction_model.pt')
    )

    return model


# ============================================================
#  Entry Point
# ============================================================

def main():
    parser = argparse.ArgumentParser(
        description='Kline Prediction Model Training'
    )

    # Mode selection
    parser.add_argument('--symbol', type=str, default=None,
                        help='Single stock symbol')
    parser.add_argument('--all', action='store_true',
                        help='Full A-share training mode')

    # Training params (from Java caller)
    parser.add_argument('--epochs', type=int, default=100)
    parser.add_argument('--batch_size', type=int, default=64)
    parser.add_argument('--seq_length', type=int, default=60)
    parser.add_argument('--pred_horizon', type=int, default=5)
    parser.add_argument('--lr', type=float, default=0.0005)

    # Internal params
    parser.add_argument('--dir_threshold', type=float, default=0.005)
    parser.add_argument('--dir_weight', type=float, default=1.0)
    parser.add_argument('--mag_weight', type=float, default=1.0)
    parser.add_argument('--grad_clip', type=float, default=1.0)
    parser.add_argument('--warmup_ratio', type=float, default=0.1)
    parser.add_argument('--min_records', type=int, default=300)
    parser.add_argument('--cache_dir', type=str, default='./seq_cache')
    parser.add_argument('--output_dir', type=str, default='../backend/models')
    parser.add_argument('--device', type=str, default='cpu')
    parser.add_argument('--cpu_limit', type=float, default=0.8)
    parser.add_argument('--feature_workers', type=int, default=None,
                        help='Workers for parallel feature computation')

    # Database params
    parser.add_argument('--db_host', type=str, default='localhost')
    parser.add_argument('--db_port', type=int, default=3306)
    parser.add_argument('--db_user', type=str, default='root')
    parser.add_argument('--db_password', type=str, default='')
    parser.add_argument('--db_name', type=str, default='tradingx')
    parser.add_argument('--start_date', type=str, default=None)
    parser.add_argument('--end_date', type=str, default=None)

    args = parser.parse_args()

    db_config = {
        'host': args.db_host,
        'port': args.db_port,
        'user': args.db_user,
        'password': args.db_password,
        'database': args.db_name,
    }

    if args.all:
        # ============ Full A-share training ============
        print("=" * 60, flush=True)
        print("MODE: Full A-share training", flush=True)
        print("=" * 60, flush=True)

        registry = StockRegistry()
        result = prepare_full_training_data(
            db_config=db_config,
            cache_dir=args.cache_dir,
            registry=registry,
            seq_length=args.seq_length,
            pred_horizon=args.pred_horizon,
            min_records=args.min_records,
            feature_workers=args.feature_workers,
            start_date=args.start_date,
            end_date=args.end_date,
            dir_threshold=args.dir_threshold,
        )

        if result is None:
            print("Data preparation failed", flush=True)
            sys.exit(1)

        seq_pool, train_dataset, val_dataset, train_sampler = result

        registry.save(os.path.join(args.output_dir, 'stock_registry.json'))

        train_model(
            train_dataset=train_dataset,
            val_dataset=val_dataset,
            train_sampler=train_sampler,
            registry=registry,
            epochs=args.epochs,
            batch_size=args.batch_size,
            seq_length=args.seq_length,
            lr=args.lr,
            dir_weight=args.dir_weight,
            mag_weight=args.mag_weight,
            grad_clip=args.grad_clip,
            warmup_ratio=args.warmup_ratio,
            device=args.device,
            output_dir=args.output_dir,
            use_embedding=True,
        )

    elif args.symbol:
        # ============ Single stock training ============
        print("=" * 60, flush=True)
        print(f"MODE: Single stock training ({args.symbol})", flush=True)
        print("=" * 60, flush=True)

        result = prepare_single_stock_data(
            symbol=args.symbol,
            db_config=db_config,
            seq_length=args.seq_length,
            pred_horizon=args.pred_horizon,
            min_records=args.min_records,
            start_date=args.start_date,
            end_date=args.end_date,
            dir_threshold=args.dir_threshold,
        )

        if result is None:
            print("Data preparation failed", flush=True)
            sys.exit(1)

        pool, train_dataset, val_dataset, _ = result

        train_model(
            train_dataset=train_dataset,
            val_dataset=val_dataset,
            epochs=args.epochs,
            batch_size=args.batch_size,
            seq_length=args.seq_length,
            lr=args.lr,
            dir_weight=args.dir_weight,
            mag_weight=args.mag_weight,
            grad_clip=args.grad_clip,
            warmup_ratio=args.warmup_ratio,
            device=args.device,
            output_dir=args.output_dir,
            use_embedding=False,
        )

    else:
        # ============ Sample data training ============
        print("No symbol specified, using sample data", flush=True)

        np.random.seed(42)
        n = 3000
        base = 100 + np.cumsum(np.random.randn(n) * 0.5)
        opens = base + np.random.randn(n) * 0.3
        highs = np.maximum(opens, base) + np.abs(np.random.randn(n)) * 0.5
        lows = np.minimum(opens, base) - np.abs(np.random.randn(n)) * 0.5
        closes = base + np.random.randn(n) * 0.2
        volumes = np.random.randint(5000, 50000, n).astype(float)

        from features import create_sequences
        feat = extract_all_features(opens, highs, lows, closes, volumes)
        X, y_mag = create_sequences(feat, closes,
                                    args.seq_length, args.pred_horizon)

        stock_ids = np.zeros(len(X), dtype=np.int64)
        pool = NumpySequencePool(X, y_mag, stock_ids,
                                 dir_threshold=args.dir_threshold)

        val_count = max(1, int(len(X) * 0.15))
        train_indices = np.arange(0, len(X) - val_count, dtype=np.int64)
        val_indices = np.arange(len(X) - val_count, len(X), dtype=np.int64)

        train_dataset = SequenceDataset(pool, train_indices)
        val_dataset = SequenceDataset(pool, val_indices)

        train_model(
            train_dataset=train_dataset,
            val_dataset=val_dataset,
            epochs=args.epochs,
            batch_size=args.batch_size,
            seq_length=args.seq_length,
            lr=args.lr,
            device=args.device,
            output_dir=args.output_dir,
        )


if __name__ == "__main__":
    main()
