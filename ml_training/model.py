# -*- coding: utf-8 -*-
import math
import torch
import torch.nn as nn


class PositionalEncoding(nn.Module):
    def __init__(self, d_model, max_len=5000, dropout=0.1):
        super().__init__()
        self.dropout = nn.Dropout(p=dropout)
        pe = torch.zeros(max_len, d_model)
        position = torch.arange(0, max_len, dtype=torch.float).unsqueeze(1)
        div_term = torch.exp(
            torch.arange(0, d_model, 2).float()
            * (-math.log(10000.0) / d_model)
        )
        pe[:, 0::2] = torch.sin(position * div_term)
        pe[:, 1::2] = torch.cos(position * div_term)
        pe = pe.unsqueeze(0)
        self.register_buffer('pe', pe)

    def forward(self, x):
        x = x + self.pe[:, :x.size(1)]
        return self.dropout(x)


class StockEmbedding(nn.Module):
    """
    股票嵌入模块

    三路信息融合：
    a. 股票级嵌入：捕捉个股独特行为模式
    b. 行业级嵌入：捕捉行业轮动、板块联动
    通过门控调制注入模型，而非简单拼接到输入
    """

    def __init__(self, num_stocks, num_sectors, embedding_dim=32):
        super().__init__()
        self.stock_emb = nn.Embedding(num_stocks, embedding_dim, padding_idx=0)
        self.sector_emb = nn.Embedding(num_sectors, embedding_dim // 2,
                                       padding_idx=0)

        total_dim = embedding_dim + embedding_dim // 2
        self.fusion = nn.Sequential(
            nn.Linear(total_dim, embedding_dim),
            nn.GELU(),
            nn.LayerNorm(embedding_dim),
            nn.Linear(embedding_dim, embedding_dim),
        )

    def forward(self, stock_idx, sector_idx):
        s = self.stock_emb(stock_idx)
        sec = self.sector_emb(sector_idx)
        combined = torch.cat([s, sec], dim=-1)
        return self.fusion(combined)


class KlineTransformer(nn.Module):
    """
    K线预测Transformer

    支持两种模式：
    1. 无embedding：input_dim=37，原始特征直接输入
    2. 有embedding：input_dim=37，embedding通过门控调制输入特征

    输出：
    - direction_logits: (batch, 3) 三分类 logits
    - magnitude: (batch,) 回归预测值
    """

    def __init__(
        self,
        input_dim=37,
        d_model=256,
        nhead=8,
        num_layers=6,
        dim_feedforward=1024,
        dropout=0.15,
        max_len=120,
        use_stock_embedding=False,
        num_stocks=1,
        num_sectors=1,
        stock_emb_dim=32,
    ):
        super().__init__()
        self.use_stock_embedding = use_stock_embedding
        self.stock_emb_dim = stock_emb_dim

        if use_stock_embedding:
            self.stock_embedding = StockEmbedding(
                num_stocks, num_sectors, stock_emb_dim
            )
            # 门控调制：embedding控制哪些特征维度被调整
            self.emb_gate = nn.Sequential(
                nn.Linear(stock_emb_dim, input_dim),
                nn.Sigmoid()
            )
            self.emb_shift = nn.Linear(stock_emb_dim, input_dim)

        self.input_proj = nn.Sequential(
            nn.Linear(input_dim, d_model),
            nn.GELU(),
            nn.LayerNorm(d_model),
        )
        self.pos_encoder = PositionalEncoding(d_model, max_len, dropout)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=d_model,
            nhead=nhead,
            dim_feedforward=dim_feedforward,
            dropout=dropout,
            activation='gelu',
            batch_first=True,
            norm_first=True,
        )
        self.transformer_encoder = nn.TransformerEncoder(
            encoder_layer,
            num_layers=num_layers,
            enable_nested_tensor=False,
        )

        self.pool_proj = nn.Sequential(
            nn.Linear(d_model, d_model),
            nn.GELU(),
            nn.LayerNorm(d_model),
        )

        self.direction_head = nn.Sequential(
            nn.Linear(d_model, d_model // 2),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(d_model // 2, 3),
        )

        self.magnitude_head = nn.Sequential(
            nn.Linear(d_model, d_model // 2),
            nn.GELU(),
            nn.Dropout(dropout),
            nn.Linear(d_model // 2, 1),
        )

        self._init_weights()

    def _init_weights(self):
        for m in self.modules():
            if isinstance(m, nn.Linear):
                nn.init.xavier_uniform_(m.weight)
                if m.bias is not None:
                    nn.init.zeros_(m.bias)

    def forward(self, x, stock_idx=None, sector_idx=None):
        """
        x:         (batch, seq_len, 37) 技术指标特征
        stock_idx:  (batch,) 股票ID（可选）
        sector_idx: (batch,) 行业ID（可选）
        """
        if (self.use_stock_embedding
                and stock_idx is not None
                and sector_idx is not None):
            emb = self.stock_embedding(stock_idx, sector_idx)
            gate = self.emb_gate(emb).unsqueeze(1)
            shift = self.emb_shift(emb).unsqueeze(1)
            x = x * gate + shift

        x = self.input_proj(x)
        x = self.pos_encoder(x)
        x = self.transformer_encoder(x)

        x_last = x[:, -1, :]
        x_pooled = self.pool_proj(x_last)

        direction_logits = self.direction_head(x_pooled)
        magnitude = self.magnitude_head(x_pooled)

        return direction_logits, magnitude.squeeze(-1)

    def export_torchscript(self, seq_length, output_path):
        self.eval()
        example_input = torch.randn(1, seq_length, 37)

        if self.use_stock_embedding:
            stock_ex = torch.tensor([0])
            sector_ex = torch.tensor([0])
            traced = torch.jit.trace(
                self, (example_input, stock_ex, sector_ex),
                check_trace=False
            )
        else:
            traced = torch.jit.trace(self, example_input, check_trace=False)

        traced.save(output_path)
        print(f"Model exported: {output_path}")
