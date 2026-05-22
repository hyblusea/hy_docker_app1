# -*- coding: utf-8 -*-
import numpy as np
import pywt


CLOSE_CHANGE_IDX = 35


# ============================================================
#  技术指标计算函数
# ============================================================

def compute_ema(values, period):
    ema = np.zeros_like(values, dtype=np.float64)
    ema[0] = values[0]
    k = 2.0 / (period + 1)
    for i in range(1, len(values)):
        ema[i] = values[i] * k + ema[i - 1] * (1 - k)
    return ema


def compute_rsi(prices, period=14):
    n = len(prices)
    rsi = np.zeros(n)
    if n < period + 1:
        return rsi
    deltas = np.diff(prices)
    gains = np.where(deltas > 0, deltas, 0)
    losses = np.where(deltas < 0, -deltas, 0)
    avg_gain = np.mean(gains[:period])
    avg_loss = np.mean(losses[:period])
    rsi[period] = 100 - (100 / (1 + (avg_gain / avg_loss
                                      if avg_loss != 0 else 100)))
    for i in range(period + 1, n):
        avg_gain = (avg_gain * (period - 1) + gains[i - 1]) / period
        avg_loss = (avg_loss * (period - 1) + losses[i - 1]) / period
        rs = avg_gain / avg_loss if avg_loss != 0 else 100
        rsi[i] = 100 - (100 / (1 + rs))
    return rsi


def compute_macd(prices, fast=12, slow=26, signal=9):
    n = len(prices)
    macd = np.zeros((n, 3))
    if n < slow:
        return macd
    ema_fast = compute_ema(prices, fast)
    ema_slow = compute_ema(prices, slow)
    diff = ema_fast - ema_slow
    signal_line = compute_ema(diff, signal)
    macd[:, 0] = diff
    macd[:, 1] = signal_line
    macd[:, 2] = diff - signal_line
    return macd


def compute_kdj(highs, lows, closes, n=9, m1=3, m2=3):
    n_len = len(closes)
    kdj = np.zeros((n_len, 3))
    if n_len < 9:
        return kdj
    k = np.zeros(n_len)
    d = np.zeros(n_len)
    for i in range(8, n_len):
        low = np.min(lows[i - 8:i + 1])
        high = np.max(highs[i - 8:i + 1])
        rsv = ((closes[i] - low) / (high - low) * 100
               if high != low else 50)
        k[i] = (k[i - 1] * (m1 - 1) / m1 + rsv / m1) if i > 8 else 50
        d[i] = (d[i - 1] * (m2 - 1) / m2 + k[i] / m2) if i > 8 else 50
        kdj[i, 0] = k[i]
        kdj[i, 1] = d[i]
        kdj[i, 2] = 3 * k[i] - 2 * d[i]
    return kdj


def compute_bollinger(prices, period=20):
    n = len(prices)
    bands = np.zeros((n, 4))
    if n < period:
        return bands
    for i in range(period - 1, n):
        window = prices[i - period + 1:i + 1]
        mean = np.mean(window)
        std = np.std(window)
        upper = mean + 2 * std
        lower = mean - 2 * std
        bands[i, 0] = upper
        bands[i, 1] = mean
        bands[i, 2] = ((prices[i] - lower) / (upper - lower)
                        if upper != lower else 0.5)
        bands[i, 3] = (upper - lower) / mean if mean != 0 else 0
    return bands


def compute_atr(highs, lows, closes, period=14):
    n = len(closes)
    atr = np.zeros(n)
    if n < period + 1:
        return atr
    tr = np.zeros(n)
    for i in range(1, n):
        hl = highs[i] - lows[i]
        hc = abs(highs[i] - closes[i - 1])
        lc = abs(lows[i] - closes[i - 1])
        tr[i] = max(hl, hc, lc)
    atr[period] = np.mean(tr[1:period + 1])
    for i in range(period + 1, n):
        atr[i] = (atr[i - 1] * (period - 1) + tr[i]) / period
    return atr


def compute_roc(prices, period=12):
    n = len(prices)
    roc = np.zeros(n)
    for i in range(period, n):
        roc[i] = ((prices[i] - prices[i - period]) / prices[i - period]
                  if prices[i - period] != 0 else 0)
    return roc


def compute_williams_r(highs, lows, closes, period=14):
    n = len(closes)
    wr = np.zeros(n)
    for i in range(period - 1, n):
        high = np.max(highs[i - period + 1:i + 1])
        low = np.min(lows[i - period + 1:i + 1])
        wr[i] = (-100 * (high - closes[i]) / (high - low)
                 if high != low else -50)
    return wr


def compute_obv_change(closes, volumes, period=5):
    n = len(closes)
    obv = np.zeros(n)
    obv_change = np.zeros(n)
    obv[0] = volumes[0]
    for i in range(1, n):
        if closes[i] > closes[i - 1]:
            obv[i] = obv[i - 1] + volumes[i]
        elif closes[i] < closes[i - 1]:
            obv[i] = obv[i - 1] - volumes[i]
        else:
            obv[i] = obv[i - 1]
    for i in range(period, n):
        obv_change[i] = ((obv[i] - obv[i - period]) / obv[i - period]
                         if obv[i - period] != 0 else 0)
    return obv_change


def compute_volume_ratio(volumes, period=20):
    n = len(volumes)
    ratio = np.zeros(n)
    for i in range(period - 1, n):
        mean = np.mean(volumes[i - period + 1:i + 1])
        ratio[i] = volumes[i] / mean if mean != 0 else 1
    return ratio


def compute_volatility(prices, period=20):
    n = len(prices)
    vol = np.zeros(n)
    for i in range(period, n):
        rets = []
        for j in range(i - period + 1, i + 1):
            ret = ((prices[j] - prices[j - 1]) / prices[j - 1]
                   if prices[j - 1] != 0 else 0)
            rets.append(ret ** 2)
        vol[i] = np.sqrt(np.mean(rets))
    return vol


def compute_mfi(highs, lows, closes, volumes, period=14):
    n = len(closes)
    mfi = np.zeros(n)
    if n < period + 1:
        return mfi
    typical_price = (highs + lows + closes) / 3
    raw_mf = typical_price * volumes
    direction = np.zeros(n)
    direction[1:] = np.sign(np.diff(typical_price))
    pos_mf = np.where(direction > 0, raw_mf, 0)
    neg_mf = np.where(direction < 0, raw_mf, 0)
    for i in range(period, n):
        pos_sum = np.sum(pos_mf[i - period + 1:i + 1])
        neg_sum = np.sum(neg_mf[i - period + 1:i + 1])
        if neg_sum != 0:
            mfr = pos_sum / neg_sum
            mfi[i] = 100 - (100 / (1 + mfr))
        else:
            mfi[i] = 100
    return mfi


def compute_adx(highs, lows, closes, period=14):
    n = len(closes)
    adx = np.zeros(n)
    if n < 2 * period + 1:
        return adx
    plus_dm = np.zeros(n)
    minus_dm = np.zeros(n)
    tr = np.zeros(n)
    for i in range(1, n):
        up_move = highs[i] - highs[i - 1]
        down_move = lows[i - 1] - lows[i]
        if up_move > down_move and up_move > 0:
            plus_dm[i] = up_move
        if down_move > up_move and down_move > 0:
            minus_dm[i] = down_move
        hl = highs[i] - lows[i]
        hc = abs(highs[i] - closes[i - 1])
        lc = abs(lows[i] - closes[i - 1])
        tr[i] = max(hl, hc, lc)
    smooth_tr = np.sum(tr[1:period + 1])
    smooth_plus_dm = np.sum(plus_dm[1:period + 1])
    smooth_minus_dm = np.sum(minus_dm[1:period + 1])
    plus_di_arr = np.zeros(n)
    minus_di_arr = np.zeros(n)
    dx_arr = np.zeros(n)
    if smooth_tr != 0:
        plus_di_arr[period] = 100 * smooth_plus_dm / smooth_tr
        minus_di_arr[period] = 100 * smooth_minus_dm / smooth_tr
    di_sum = plus_di_arr[period] + minus_di_arr[period]
    if di_sum != 0:
        dx_arr[period] = (100 * abs(plus_di_arr[period]
                                    - minus_di_arr[period]) / di_sum)
    for i in range(period + 1, n):
        smooth_tr = smooth_tr - smooth_tr / period + tr[i]
        smooth_plus_dm = (smooth_plus_dm
                          - smooth_plus_dm / period + plus_dm[i])
        smooth_minus_dm = (smooth_minus_dm
                           - smooth_minus_dm / period + minus_dm[i])
        if smooth_tr != 0:
            plus_di_arr[i] = 100 * smooth_plus_dm / smooth_tr
            minus_di_arr[i] = 100 * smooth_minus_dm / smooth_tr
        di_sum = plus_di_arr[i] + minus_di_arr[i]
        if di_sum != 0:
            dx_arr[i] = (100 * abs(plus_di_arr[i]
                                    - minus_di_arr[i]) / di_sum)
    adx_start = 2 * period
    if adx_start < n:
        adx[adx_start] = np.mean(dx_arr[period:adx_start + 1])
        for i in range(adx_start + 1, n):
            adx[i] = (adx[i - 1] * (period - 1) + dx_arr[i]) / period
    return adx


def extract_wavelet_features(prices, levels=3, coeff_len=64):
    n = len(prices)
    num_features = 2 * (levels + 1)
    features = np.zeros((n, num_features))
    for i in range(coeff_len - 1, n):
        window = prices[i - coeff_len + 1:i + 1]
        coeffs = pywt.wavedec(window, 'db4', level=levels)
        for level in range(levels + 1):
            if level < len(coeffs):
                level_coeffs = coeffs[level]
                features[i, 2 * level] = np.std(level_coeffs)
                features[i, 2 * level + 1] = np.mean(level_coeffs)
    return features


# ============================================================
#  37维特征提取
# ============================================================

def extract_all_features(opens, highs, lows, closes, volumes):
    """
    提取单支股票的37维技术指标特征

    特征布局：
    [0-2]   RSI(6,14,24)              -> (x-50)/50
    [3-5]   MACD (diff/signal/hist)   -> tanh(x/base)
    [6-8]   KDJ (K, D, J)             -> x/100
    [9-10]  布林带 (位置, 带宽)        -> 原值, tanh
    [11-13] ATR(7, 14, 28)            -> tanh(x/base)
    [14-16] ROC(6, 12, 24)            -> tanh(x)
    [17]    Williams %R                -> x/100
    [18]    OBV变动率                  -> tanh(x)
    [19-20] 量比(5, 20)               -> tanh(x-1)
    [21]    波动率                     -> tanh(x)
    [22-29] 小波特征 (4层x2)           -> tanh(x)
    [30]    MFI                        -> (x-50)/50
    [31]    ADX                        -> x/100
    [32-36] K线形态                    -> tanh(比率), tanh(log)
    """
    n = len(closes)
    if n == 0:
        return np.zeros((0, 37))

    features = np.zeros((n, 37))
    base = np.where(closes != 0, closes, 1.0)

    prev_closes = np.empty(n)
    prev_closes[0] = closes[0]
    prev_closes[1:] = closes[:-1]

    rsi_6 = compute_rsi(closes, period=6)
    rsi_14 = compute_rsi(closes, period=14)
    rsi_24 = compute_rsi(closes, period=24)
    macd = compute_macd(closes)
    kdj = compute_kdj(highs, lows, closes)
    bollinger = compute_bollinger(closes)
    atr_7 = compute_atr(highs, lows, closes, period=7)
    atr_14 = compute_atr(highs, lows, closes, period=14)
    atr_28 = compute_atr(highs, lows, closes, period=28)
    roc_6 = compute_roc(closes, period=6)
    roc_12 = compute_roc(closes, period=12)
    roc_24 = compute_roc(closes, period=24)
    williams_r = compute_williams_r(highs, lows, closes)
    obv_change = compute_obv_change(closes, volumes)
    vol_ratio_5 = compute_volume_ratio(volumes, period=5)
    vol_ratio_20 = compute_volume_ratio(volumes, period=20)
    volatility = compute_volatility(closes)
    wavelet = extract_wavelet_features(closes)
    mfi = compute_mfi(highs, lows, closes, volumes)
    adx = compute_adx(highs, lows, closes)

    features[:, 0] = (rsi_6 - 50) / 50
    features[:, 1] = (rsi_14 - 50) / 50
    features[:, 2] = (rsi_24 - 50) / 50
    features[:, 3] = np.tanh(macd[:, 0] / base)
    features[:, 4] = np.tanh(macd[:, 1] / base)
    features[:, 5] = np.tanh(macd[:, 2] / base)
    features[:, 6] = kdj[:, 0] / 100
    features[:, 7] = kdj[:, 1] / 100
    features[:, 8] = kdj[:, 2] / 100
    features[:, 9] = bollinger[:, 2]
    features[:, 10] = np.tanh(bollinger[:, 3])
    features[:, 11] = np.tanh(atr_7 / base)
    features[:, 12] = np.tanh(atr_14 / base)
    features[:, 13] = np.tanh(atr_28 / base)
    features[:, 14] = np.tanh(roc_6)
    features[:, 15] = np.tanh(roc_12)
    features[:, 16] = np.tanh(roc_24)
    features[:, 17] = williams_r / 100
    features[:, 18] = np.tanh(obv_change)
    features[:, 19] = np.tanh(vol_ratio_5 - 1)
    features[:, 20] = np.tanh(vol_ratio_20 - 1)
    features[:, 21] = np.tanh(volatility)
    features[:, 22:30] = np.tanh(wavelet[:, :8])
    features[:, 30] = (mfi - 50) / 50
    features[:, 31] = adx / 100
    features[:, 32] = np.tanh((opens - closes) / base)
    features[:, 33] = np.tanh((highs - closes) / base)
    features[:, 34] = np.tanh((lows - closes) / base)
    features[:, 35] = np.tanh((closes - prev_closes) / base)
    features[:, 36] = np.tanh(np.log(volumes + 1) / 15)

    # 清理 NaN/Inf，防止输入数据异常导致下游计算出错
    features = np.nan_to_num(features, nan=0.0, posinf=1.0, neginf=-1.0)

    return features


# ============================================================
#  序列创建（单支股票）
# ============================================================

def create_sequences(features, closes, seq_length=60, pred_horizon=5):
    n = features.shape[0]
    num_seqs = n - seq_length - pred_horizon + 1
    if num_seqs <= 0:
        return np.array([]), np.array([])

    X = np.zeros((num_seqs, seq_length, features.shape[1]), dtype=np.float32)
    y = np.zeros(num_seqs, dtype=np.float32)

    for i in range(num_seqs):
        X[i] = features[i:i + seq_length]
        future_idx = i + seq_length + pred_horizon - 1
        curr_idx = i + seq_length - 1
        y[i] = ((closes[future_idx] - closes[curr_idx]) / closes[curr_idx]
                if closes[curr_idx] != 0 else 0)

    return X, y


# ============================================================
#  多股票批量处理
# ============================================================

def extract_all_features_per_stock(stock_data_list):
    all_features = []
    stock_boundaries = []
    offset = 0
    for stock_data in stock_data_list:
        opens, highs, lows, closes, volumes = stock_data
        feat = extract_all_features(opens, highs, lows, closes, volumes)
        all_features.append(feat)
        end = offset + len(feat)
        stock_boundaries.append((offset, end))
        offset = end
    return np.concatenate(all_features, axis=0), stock_boundaries


def create_sequences_per_stock(stock_data_list, seq_length=60, pred_horizon=5):
    all_X = []
    all_y = []
    for stock_data in stock_data_list:
        opens, highs, lows, closes, volumes = stock_data
        if len(closes) < seq_length + pred_horizon:
            print(f"Stock data too short ({len(closes)} points), skipping")
            continue
        feat = extract_all_features(opens, highs, lows, closes, volumes)
        X, y = create_sequences(feat, closes, seq_length, pred_horizon)
        if len(X) > 0:
            all_X.append(X)
            all_y.append(y)
    if not all_X:
        return np.array([]), np.array([])
    return np.concatenate(all_X, axis=0), np.concatenate(all_y, axis=0)
