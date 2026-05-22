package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TrackStockVO {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("ts_code")
    private String tsCode;

    @JsonProperty("stock_name")
    private String stockName;

    @JsonProperty("strategy_id")
    private Long strategyId;

    @JsonProperty("strategy_name")
    private String strategyName;

    @JsonProperty("add_date")
    private String addDate;

    @JsonProperty("add_price")
    private Double addPrice;

    @JsonProperty("current_price")
    private Double currentPrice;

    @JsonProperty("change_rate")
    private Double changeRate;

    @JsonProperty("change_amount")
    private Double changeAmount;

    @JsonProperty("today_change")
    private Double todayChange;

    @JsonProperty("today_pct")
    private Double todayPct;

    public static TrackStockVO fromEntity(TrackStockEntity entity, Double addPrice, Double currentPrice, Double prevClose) {
        TrackStockVO vo = new TrackStockVO();
        vo.id = entity.getId();
        vo.tsCode = entity.getTsCode();
        vo.stockName = entity.getStockName();
        vo.strategyId = entity.getStrategyId();
        vo.strategyName = entity.getStrategyName();
        vo.addDate = entity.getAddDate();
        vo.addPrice = addPrice;
        vo.currentPrice = currentPrice;
        if (addPrice != null && currentPrice != null && addPrice != 0) {
            vo.changeRate = (currentPrice - addPrice) / addPrice * 100;
            vo.changeAmount = currentPrice - addPrice;
        }
        if (prevClose != null && currentPrice != null && prevClose != 0) {
            vo.todayChange = currentPrice - prevClose;
            vo.todayPct = (currentPrice - prevClose) / prevClose * 100;
        }
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTsCode() { return tsCode; }
    public void setTsCode(String tsCode) { this.tsCode = tsCode; }

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }

    public Long getStrategyId() { return strategyId; }
    public void setStrategyId(Long strategyId) { this.strategyId = strategyId; }

    public String getStrategyName() { return strategyName; }
    public void setStrategyName(String strategyName) { this.strategyName = strategyName; }

    public String getAddDate() { return addDate; }
    public void setAddDate(String addDate) { this.addDate = addDate; }

    public Double getAddPrice() { return addPrice; }
    public void setAddPrice(Double addPrice) { this.addPrice = addPrice; }

    public Double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }

    public Double getChangeRate() { return changeRate; }
    public void setChangeRate(Double changeRate) { this.changeRate = changeRate; }

    public Double getChangeAmount() { return changeAmount; }
    public void setChangeAmount(Double changeAmount) { this.changeAmount = changeAmount; }

    public Double getTodayChange() { return todayChange; }
    public void setTodayChange(Double todayChange) { this.todayChange = todayChange; }

    public Double getTodayPct() { return todayPct; }
    public void setTodayPct(Double todayPct) { this.todayPct = todayPct; }
}
