# -*- coding: utf-8 -*-
import json
import os
import re


class StockRegistry:
    """
    股票注册表

    职责：
    1. symbol → 整数ID 的双向映射
    2. 行业分类映射
    3. 持久化/加载

    ID分配规则：
    - 0号保留给未知股票（推理时新股票用）
    - 行业0号保留给未知行业
    """

    # 申万一级行业分类（基于股票代码前缀的简单推断）
    # 真实生产环境应从数据库或第三方API获取
    CODE_SECTOR_MAP = {
        # 60开头：上交所主板
        # 00开头：深交所主板
        # 30开头：创业板
        # 68开头：科创板
    }

    def __init__(self):
        self.symbol_to_idx = {}
        self.idx_to_symbol = {}
        self.sector_to_idx = {'未知': 0}
        self.idx_to_sector = {0: '未知'}
        self.symbol_to_sector = {}
        self._next_stock_id = 1   # 0保留
        self._next_sector_id = 1  # 0保留

    def infer_sector(self, symbol):
        """
        基于股票代码推断行业板块

        简化方案：按板块分类
        生产环境应接入申万行业分类数据
        """
        if symbol.startswith('68'):
            return '科创板'
        elif symbol.startswith('30'):
            return '创业板'
        elif symbol.startswith('00'):
            return '深交所主板'
        elif symbol.startswith('60'):
            return '上交所主板'
        else:
            return '未知'

    def register_stocks(self, symbols, sector_map=None):
        """
        批量注册股票

        参数:
            symbols: 股票代码列表
            sector_map: {symbol: sector_name} 可选，提供精确行业分类
        """
        for symbol in symbols:
            if symbol not in self.symbol_to_idx:
                self.symbol_to_idx[symbol] = self._next_stock_id
                self.idx_to_symbol[self._next_stock_id] = symbol
                self._next_stock_id += 1

            # 行业映射
            if sector_map and symbol in sector_map:
                sector = sector_map[symbol]
            else:
                sector = self.infer_sector(symbol)

            self.symbol_to_sector[symbol] = sector

            if sector not in self.sector_to_idx:
                self.sector_to_idx[sector] = self._next_sector_id
                self.idx_to_sector[self._next_sector_id] = sector
                self._next_sector_id += 1

        print(f"Registry: {self._next_stock_id - 1} stocks, "
              f"{self._next_sector_id - 1} sectors")

    def get_stock_idx(self, symbol):
        return self.symbol_to_idx.get(symbol, 0)

    def get_sector_idx(self, symbol):
        sector = self.symbol_to_sector.get(symbol, '未知')
        return self.sector_to_idx.get(sector, 0)

    def get_batch_indices(self, symbols):
        """批量获取ID，用于numpy向量化操作"""
        stock_ids = []
        sector_ids = []
        for s in symbols:
            stock_ids.append(self.get_stock_idx(s))
            sector_ids.append(self.get_sector_idx(s))
        return stock_ids, sector_ids

    def save(self, path):
        os.makedirs(os.path.dirname(path) or '.', exist_ok=True)
        data = {
            'symbol_to_idx': self.symbol_to_idx,
            'sector_to_idx': self.sector_to_idx,
            'symbol_to_sector': self.symbol_to_sector,
            'next_stock_id': self._next_stock_id,
            'next_sector_id': self._next_sector_id,
        }
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"Registry saved: {path}")

    def load(self, path):
        with open(path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        self.symbol_to_idx = data['symbol_to_idx']
        self.idx_to_symbol = {int(v): k
                              for k, v in self.symbol_to_idx.items()}
        self.sector_to_idx = data['sector_to_idx']
        self.idx_to_sector = {int(v): k
                              for k, v in self.sector_to_idx.items()}
        self.symbol_to_sector = data['symbol_to_sector']
        self._next_stock_id = data['next_stock_id']
        self._next_sector_id = data['next_sector_id']
        print(f"Registry loaded: {self._next_stock_id - 1} stocks, "
              f"{self._next_sector_id - 1} sectors")

    @property
    def num_stocks(self):
        return self._next_stock_id

    @property
    def num_sectors(self):
        return self._next_sector_id
