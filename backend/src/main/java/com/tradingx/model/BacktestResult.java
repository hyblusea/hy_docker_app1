package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

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

    public static class BacktestSignal {
        private String tradeDate;
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

        @JsonProperty("trade_date")
        public String getTradeDate() { return tradeDate; }
        @JsonProperty("trade_date")
        public void setTradeDate(String tradeDate) { this.tradeDate = tradeDate; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        
        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        
        public double getShares() { return shares; }
        public void setShares(double shares) { this.shares = shares; }
        
        @JsonProperty("buy_amount")
        public double getBuyAmount() { return buyAmount; }
        @JsonProperty("buy_amount")
        public void setBuyAmount(double buyAmount) { this.buyAmount = buyAmount; }
        
        public double getFees() { return fees; }
        public void setFees(double fees) { this.fees = fees; }
        
        @JsonProperty("sell_fees")
        public double getSellFees() { return sellFees; }
        @JsonProperty("sell_fees")
        public void setSellFees(double sellFees) { this.sellFees = sellFees; }
        
        public double getProfit() { return profit; }
        public void setProfit(double profit) { this.profit = profit; }
        
        @JsonProperty("profit_pct")
        public double getProfitPct() { return profitPct; }
        @JsonProperty("profit_pct")
        public void setProfitPct(double profitPct) { this.profitPct = profitPct; }
        
        @JsonProperty("remaining_cash")
        public double getRemainingCash() { return remainingCash; }
        @JsonProperty("remaining_cash")
        public void setRemainingCash(double remainingCash) { this.remainingCash = remainingCash; }
    }

    public List<BacktestSignal> getSignals() { return signals; }
    public void setSignals(List<BacktestSignal> signals) { this.signals = signals; }
    
    @JsonProperty("total_return")
    public double getTotalReturn() { return totalReturn; }
    @JsonProperty("total_return")
    public void setTotalReturn(double totalReturn) { this.totalReturn = totalReturn; }
    
    @JsonProperty("win_rate")
    public double getWinRate() { return winRate; }
    @JsonProperty("win_rate")
    public void setWinRate(double winRate) { this.winRate = winRate; }
    
    @JsonProperty("trade_count")
    public int getTradeCount() { return tradeCount; }
    @JsonProperty("trade_count")
    public void setTradeCount(int tradeCount) { this.tradeCount = tradeCount; }
    
    @JsonProperty("winning_trades")
    public int getWinningTrades() { return winningTrades; }
    @JsonProperty("winning_trades")
    public void setWinningTrades(int winningTrades) { this.winningTrades = winningTrades; }
    
    @JsonProperty("losing_trades")
    public int getLosingTrades() { return losingTrades; }
    @JsonProperty("losing_trades")
    public void setLosingTrades(int losingTrades) { this.losingTrades = losingTrades; }
    
    @JsonProperty("max_drawdown")
    public double getMaxDrawdown() { return maxDrawdown; }
    @JsonProperty("max_drawdown")
    public void setMaxDrawdown(double maxDrawdown) { this.maxDrawdown = maxDrawdown; }
    
    @JsonProperty("profit_loss")
    public double getProfitLoss() { return profitLoss; }
    @JsonProperty("profit_loss")
    public void setProfitLoss(double profitLoss) { this.profitLoss = profitLoss; }
    
    @JsonProperty("open_position_count")
    public int getOpenPositionCount() { return openPositionCount; }
    @JsonProperty("open_position_count")
    public void setOpenPositionCount(int openPositionCount) { this.openPositionCount = openPositionCount; }
    
    @JsonProperty("initial_capital")
    public double getInitialCapital() { return initialCapital; }
    @JsonProperty("initial_capital")
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }
    
    @JsonProperty("final_capital")
    public double getFinalCapital() { return finalCapital; }
    @JsonProperty("final_capital")
    public void setFinalCapital(double finalCapital) { this.finalCapital = finalCapital; }
    
    @JsonProperty("total_fees")
    public double getTotalFees() { return totalFees; }
    @JsonProperty("total_fees")
    public void setTotalFees(double totalFees) { this.totalFees = totalFees; }
}
