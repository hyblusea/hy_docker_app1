package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class StrategyAnalysisResult {
    private String taskId;
    private Long strategyId;
    private String strategyName;
    private int totalStocks;
    private int winningStocks;
    private int losingStocks;
    private int profitableStocks;
    private double avgReturn;
    private double maxReturn;
    private String maxReturnStock;
    private double minReturn;
    private String minReturnStock;
    private double avgWinRate;
    private double avgMaxDrawdown;
    private int totalTrades;
    private double avgTradeCount;
    private double profitFactor;
    private double totalProfit;
    private double totalLoss;
    private List<StockPerformance> performances;
    private List<StockPerformance> topStocks;
    private List<StockPerformance> worstStocks;

    public static class StockPerformance {
        private String tsCode;
        private String stockName;
        private double totalReturn;
        private double winRate;
        private int tradeCount;
        private double maxDrawdown;
        private BacktestResult backtestResult;

        @JsonProperty("ts_code")
        public String getTsCode() { return tsCode; }
        @JsonProperty("ts_code")
        public void setTsCode(String tsCode) { this.tsCode = tsCode; }
        
        @JsonProperty("stock_name")
        public String getStockName() { return stockName; }
        @JsonProperty("stock_name")
        public void setStockName(String stockName) { this.stockName = stockName; }
        
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
        
        @JsonProperty("max_drawdown")
        public double getMaxDrawdown() { return maxDrawdown; }
        @JsonProperty("max_drawdown")
        public void setMaxDrawdown(double maxDrawdown) { this.maxDrawdown = maxDrawdown; }
        
        @JsonProperty("backtest_result")
        public BacktestResult getBacktestResult() { return backtestResult; }
        @JsonProperty("backtest_result")
        public void setBacktestResult(BacktestResult backtestResult) { this.backtestResult = backtestResult; }
    }

    @JsonProperty("task_id")
    public String getTaskId() { return taskId; }
    @JsonProperty("task_id")
    public void setTaskId(String taskId) { this.taskId = taskId; }
    
    @JsonProperty("strategy_id")
    public Long getStrategyId() { return strategyId; }
    @JsonProperty("strategy_id")
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }
    
    @JsonProperty("strategy_name")
    public String getStrategyName() { return strategyName; }
    @JsonProperty("strategy_name")
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }
    
    @JsonProperty("total_stocks")
    public int getTotalStocks() { return totalStocks; }
    @JsonProperty("total_stocks")
    public void setTotalStocks(int totalStocks) { this.totalStocks = totalStocks; }
    
    @JsonProperty("winning_stocks")
    public int getWinningStocks() { return winningStocks; }
    @JsonProperty("winning_stocks")
    public void setWinningStocks(int winningStocks) { this.winningStocks = winningStocks; }
    
    @JsonProperty("losing_stocks")
    public int getLosingStocks() { return losingStocks; }
    @JsonProperty("losing_stocks")
    public void setLosingStocks(int losingStocks) { this.losingStocks = losingStocks; }
    
    @JsonProperty("profitable_stocks")
    public int getProfitableStocks() { return profitableStocks; }
    @JsonProperty("profitable_stocks")
    public void setProfitableStocks(int profitableStocks) { this.profitableStocks = profitableStocks; }
    
    @JsonProperty("avg_return")
    public double getAvgReturn() { return avgReturn; }
    @JsonProperty("avg_return")
    public void setAvgReturn(double avgReturn) { this.avgReturn = avgReturn; }
    
    @JsonProperty("max_return")
    public double getMaxReturn() { return maxReturn; }
    @JsonProperty("max_return")
    public void setMaxReturn(double maxReturn) { this.maxReturn = maxReturn; }
    
    @JsonProperty("max_return_stock")
    public String getMaxReturnStock() { return maxReturnStock; }
    @JsonProperty("max_return_stock")
    public void setMaxReturnStock(String maxReturnStock) { this.maxReturnStock = maxReturnStock; }
    
    @JsonProperty("min_return")
    public double getMinReturn() { return minReturn; }
    @JsonProperty("min_return")
    public void setMinReturn(double minReturn) { this.minReturn = minReturn; }
    
    @JsonProperty("min_return_stock")
    public String getMinReturnStock() { return minReturnStock; }
    @JsonProperty("min_return_stock")
    public void setMinReturnStock(String minReturnStock) { this.minReturnStock = minReturnStock; }
    
    @JsonProperty("avg_win_rate")
    public double getAvgWinRate() { return avgWinRate; }
    @JsonProperty("avg_win_rate")
    public void setAvgWinRate(double avgWinRate) { this.avgWinRate = avgWinRate; }
    
    @JsonProperty("avg_max_drawdown")
    public double getAvgMaxDrawdown() { return avgMaxDrawdown; }
    @JsonProperty("avg_max_drawdown")
    public void setAvgMaxDrawdown(double avgMaxDrawdown) { this.avgMaxDrawdown = avgMaxDrawdown; }
    
    @JsonProperty("total_trades")
    public int getTotalTrades() { return totalTrades; }
    @JsonProperty("total_trades")
    public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }
    
    @JsonProperty("avg_trade_count")
    public double getAvgTradeCount() { return avgTradeCount; }
    @JsonProperty("avg_trade_count")
    public void setAvgTradeCount(double avgTradeCount) { this.avgTradeCount = avgTradeCount; }
    
    @JsonProperty("profit_factor")
    public double getProfitFactor() { return profitFactor; }
    @JsonProperty("profit_factor")
    public void setProfitFactor(double profitFactor) { this.profitFactor = profitFactor; }

    @JsonProperty("total_profit")
    public double getTotalProfit() { return totalProfit; }
    @JsonProperty("total_profit")
    public void setTotalProfit(double totalProfit) { this.totalProfit = totalProfit; }

    @JsonProperty("total_loss")
    public double getTotalLoss() { return totalLoss; }
    @JsonProperty("total_loss")
    public void setTotalLoss(double totalLoss) { this.totalLoss = totalLoss; }

    public List<StockPerformance> getPerformances() { return performances; }
    public void setPerformances(List<StockPerformance> performances) { this.performances = performances; }
    
    @JsonProperty("top_stocks")
    public List<StockPerformance> getTopStocks() { return topStocks; }
    @JsonProperty("top_stocks")
    public void setTopStocks(List<StockPerformance> topStocks) { this.topStocks = topStocks; }
    
    @JsonProperty("worst_stocks")
    public List<StockPerformance> getWorstStocks() { return worstStocks; }
    @JsonProperty("worst_stocks")
    public void setWorstStocks(List<StockPerformance> worstStocks) { this.worstStocks = worstStocks; }
}
