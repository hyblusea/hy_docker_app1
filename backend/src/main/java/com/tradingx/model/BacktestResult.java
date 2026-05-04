package com.tradingx.model;

import lombok.Data;

import java.util.List;

@Data
public class BacktestResult {

    private List<BacktestSignal> signals;
    private double totalReturn;
    private double winRate;
    private int tradeCount;
    private int winningTrades;
    private int losingTrades;
    private double maxDrawdown;
    private double profitLoss;
    private int openPositionCount;
    private double initialCapital;
    private double finalCapital;
    private double totalFees;

    @Data
    public static class BacktestSignal {
        private String trade_date;
        private String type;
        private double price;
        private int index;
        private double shares;
        private double buyAmount;
        private double fees;
        private double sellFees;
        private double profit;
        private double profitPct;
        private double remainingCash;
    }
}
