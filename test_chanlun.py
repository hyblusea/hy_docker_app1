import urllib.request
import json

resp = urllib.request.urlopen('http://127.0.0.1:8081/api/public/stock_zh_a_daily?symbol=sz000001&start_date=20250101&end_date=20250510&adjust=')
data = json.loads(resp.read().decode())
print(f'Total bars: {len(data)}')

highs = [d['high'] for d in data]
lows = [d['low'] for d in data]
opens = [d['open'] for d in data]
closes = [d['close'] for d in data]

# Step 1: K线包含处理
merged = [{'high': highs[0], 'low': lows[0], 'direction': 'up' if closes[0] >= opens[0] else 'down', 'startIndex': 0, 'endIndex': 0}]
for i in range(1, len(highs)):
    prev = merged[-1]
    h, l = highs[i], lows[i]
    is_contain = (h <= prev['high'] and l >= prev['low']) or (h >= prev['high'] and l <= prev['low'])
    if is_contain:
        if prev['direction'] == 'up':
            prev['high'] = max(prev['high'], h); prev['low'] = max(prev['low'], l)
        else:
            prev['high'] = min(prev['high'], h); prev['low'] = min(prev['low'], l)
        prev['endIndex'] = i
    else:
        merged.append({'high': h, 'low': l, 'direction': 'up' if h > prev['high'] else 'down', 'startIndex': i, 'endIndex': i})
print(f'Merged bars: {len(merged)}')

# Step 2: 分型识别
fractals = []
for i in range(1, len(merged) - 1):
    p, c, n = merged[i-1], merged[i], merged[i+1]
    if c['high'] > p['high'] and c['high'] > n['high']:
        fractals.append({'type': 'top', 'barIndex': i, 'kIndex': c['endIndex'], 'price': c['high']})
    if c['low'] < p['low'] and c['low'] < n['low']:
        fractals.append({'type': 'bottom', 'barIndex': i, 'kIndex': c['endIndex'], 'price': c['low']})
print(f'Fractals: {len(fractals)}')

# Step 3: 笔的划分 (extend last bi on same-type extreme)
bi_list = []
last = None
for f in fractals:
    if last is None:
        last = f; continue
    if f['type'] == last['type']:
        if bi_list:
            if f['type'] == 'top' and f['price'] > last['price']:
                bi_list[-1]['end'] = f; last = f
            elif f['type'] == 'bottom' and f['price'] < last['price']:
                bi_list[-1]['end'] = f; last = f
        else:
            if f['type'] == 'top' and f['price'] > last['price']: last = f
            elif f['type'] == 'bottom' and f['price'] < last['price']: last = f
        continue
    if f['barIndex'] - last['barIndex'] < 2: continue
    if last['type'] == 'bottom' and f['type'] == 'top' and f['price'] <= last['price']: continue
    if last['type'] == 'top' and f['type'] == 'bottom' and f['price'] >= last['price']: continue
    bi_list.append({'start': last, 'end': f, 'direction': 'up' if last['type'] == 'bottom' else 'down'})
    last = f

print(f'\nBi: {len(bi_list)}')
for i, bi in enumerate(bi_list):
    s, e = bi['start'], bi['end']
    print(f'  Bi{i+1} {bi["direction"]}: {s["type"]}({s["price"]:.2f}@k{s["kIndex"]}) -> {e["type"]}({e["price"]:.2f}@k{e["kIndex"]})')

# Continuity check
gaps = sum(1 for i in range(1, len(bi_list)) if bi_list[i-1]['end']['kIndex'] != bi_list[i]['start']['kIndex'])
print(f'Continuity: {"✓ All continuous" if gaps == 0 else f"✗ {gaps} gaps"}')

# Step 4: 中枢识别 (extend method)
zs_list = []
i = 0
while i < len(bi_list) - 2:
    b1, b2, b3 = bi_list[i], bi_list[i+1], bi_list[i+2]
    h1, l1 = max(b1['start']['price'], b1['end']['price']), min(b1['start']['price'], b1['end']['price'])
    h2, l2 = max(b2['start']['price'], b2['end']['price']), min(b2['start']['price'], b2['end']['price'])
    h3, l3 = max(b3['start']['price'], b3['end']['price']), min(b3['start']['price'], b3['end']['price'])
    zs_high = min(h1, h2, h3)
    zs_low = max(l1, l2, l3)
    if zs_high <= zs_low:
        i += 1; continue
    end_bi_idx = i + 2
    for j in range(i + 3, len(bi_list)):
        bj = bi_list[j]
        bj_h = max(bj['start']['price'], bj['end']['price'])
        bj_l = min(bj['start']['price'], bj['end']['price'])
        if bj_l <= zs_high and bj_h >= zs_low:
            end_bi_idx = j
        else:
            break
    zs_list.append({'high': zs_high, 'low': zs_low, 'startBiIdx': i, 'endBiIdx': end_bi_idx})
    i = end_bi_idx + 1

print(f'\nZhongShu: {len(zs_list)}')
for i, zs in enumerate(zs_list):
    sk = bi_list[zs['startBiIdx']]['start']['kIndex']
    ek = bi_list[zs['endBiIdx']]['end']['kIndex']
    print(f'  ZS{i+1}: [{zs["low"]:.2f}..{zs["high"]:.2f}] k=[{sk}..{ek}] bi=[{zs["startBiIdx"]}..{zs["endBiIdx"]}]')

# Step 5: 买卖点
print('\nBuy/Sell Points:')
for zs in zs_list:
    nbi = zs['endBiIdx'] + 1
    if nbi >= len(bi_list): continue
    nb = bi_list[nbi]
    ep = nb['end']['price']
    if nb['direction'] == 'down':
        pt = 'buy1' if ep < zs['low'] else ('buy2' if ep <= zs['high'] else 'buy3')
    else:
        pt = 'sell1' if ep > zs['high'] else ('sell2' if ep >= zs['low'] else 'sell3')
    print(f'  {pt} at k{nb["end"]["kIndex"]}, price={ep:.2f}')
