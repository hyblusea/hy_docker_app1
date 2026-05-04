package com.tradingx.service;

import com.tradingx.model.BacktestResult;
import com.tradingx.model.DailyQuote;
import com.tradingx.model.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestService {

    private final StrategyService strategyService;
    private final StrategyCompiler strategyCompiler;
    private final VisualStrategyBuilder visualStrategyBuilder;

    private static final double INITIAL_CAPITAL = 1_000_000.0;
    private static final double STAMP_DUTY_RATE = 0.0005;
    private static final double COMMISSION_RATE = 0.0002;
    private static final double MIN_COMMISSION = 5.0;
    private static final double TRANSFER_FEE_RATE = 0.00001;

    public BacktestResult runBacktest(Long strategyId, List<DailyQuote> quotes) {
        if (quotes == null || quotes.isEmpty()) {
            throw new ValidationException("行情数据不能为空");
        }

        com.tradingx.model.Strategy strategyEntity = strategyService.getById(strategyId);

        BarSeries series = convertToBarSeries(quotes);

        org.ta4j.core.Strategy ta4jStrategy;
        if (strategyEntity.getCode() != null && !strategyEntity.getCode().isBlank()) {
            if ("visual".equalsIgnoreCase(strategyEntity.getLanguage())) {
                ta4jStrategy = visualStrategyBuilder.build(strategyEntity.getCode(), series);
            } else {
                ta4jStrategy = strategyCompiler.compileAndRun(strategyEntity.getCode(), series);
            }
        } else {
            ta4jStrategy = buildDefaultStrategy(series);
        }

        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(ta4jStrategy);

        log.info("Backtest: strategyId={}, seriesBars={}, closedPositions={}, openPositions={}, totalTrades={}",
                strategyId, series.getBarCount(),
                record.getPositions().size(),
                record.getOpenPositions().size(),
                record.getTrades().size());

        List<BacktestResult.BacktestSignal> signals = new ArrayList<>();

        BacktestResult result = new BacktestResult();
        result.setInitialCapital(INITIAL_CAPITAL);

        double equity = INITIAL_CAPITAL;
        double peakEquity = INITIAL_CAPITAL;
        double maxDrawdownPct = 0;
        int closedCount = 0;
        int wins = 0;
        int losses = 0;
        double totalFees = 0;
        double totalProfitLoss = 0;

        for (Position position : record.getPositions()) {
            if (!position.isClosed()) continue;

            closedCount++;
            double entryPrice = position.getEntry().getPricePerAsset().doubleValue();
            double exitPrice = position.getExit().getPricePerAsset().doubleValue();

            double shares = Math.floor(equity / entryPrice / 100) * 100;
            if (shares <= 0) {
                log.warn("资金不足, 跳过第{}笔交易: equity={}, entryPrice={}", closedCount, equity, entryPrice);
                continue;
            }

            double buyAmount = shares * entryPrice;
            double buyFees = calcBuyFees(buyAmount);
            double sellAmount = shares * exitPrice;
            double sellFees = calcSellFees(sellAmount);
            double fees = buyFees + sellFees;
            double profit = (exitPrice - entryPrice) * shares - fees;
            double profitPct = buyAmount > 0 ? profit / buyAmount * 100 : 0;
            double remainingCash = equity - buyAmount - buyFees;

            totalFees += fees;
            totalProfitLoss += profit;
            equity += profit;

            BacktestResult.BacktestSignal entrySignal = createSignal(position.getEntry(), series, "BUY");
            entrySignal.setShares(shares);
            entrySignal.setBuyAmount(buyAmount);
            entrySignal.setFees(buyFees);
            entrySignal.setRemainingCash(remainingCash);
            signals.add(entrySignal);

            BacktestResult.BacktestSignal exitSignal = createSignal(position.getExit(), series, "SELL");
            exitSignal.setShares(shares);
            exitSignal.setBuyAmount(buyAmount);
            exitSignal.setFees(buyFees);
            exitSignal.setSellFees(sellFees);
            exitSignal.setProfit(profit);
            exitSignal.setProfitPct(profitPct);
            exitSignal.setRemainingCash(equity);
            signals.add(exitSignal);

            if (equity > peakEquity) {
                peakEquity = equity;
            }
            double drawdownPct = peakEquity > 0 ? (peakEquity - equity) / peakEquity * 100 : 0;
            if (drawdownPct > maxDrawdownPct) {
                maxDrawdownPct = drawdownPct;
            }

            if (profit > 0) {
                wins++;
            } else {
                losses++;
            }

            if (equity <= 0) {
                equity = 0;
                log.warn("本金亏完, 停止计算: 第{}笔交易后", closedCount);
                break;
            }
        }

        for (Position pos : record.getOpenPositions()) {
            BacktestResult.BacktestSignal signal = createSignal(pos.getEntry(), series, "BUY");
            double entryPrice = pos.getEntry().getPricePerAsset().doubleValue();
            double shares = Math.floor(equity / entryPrice / 100) * 100;
            double buyAmount = shares * entryPrice;
            double buyFees = calcBuyFees(buyAmount);
            signal.setShares(shares);
            signal.setBuyAmount(buyAmount);
            signal.setFees(buyFees);
            signal.setRemainingCash(equity - buyAmount - buyFees);
            signals.add(signal);
        }

        double totalReturnPct = INITIAL_CAPITAL > 0
                ? (equity - INITIAL_CAPITAL) / INITIAL_CAPITAL * 100
                : 0;

        result.setTradeCount(closedCount);
        result.setWinningTrades(wins);
        result.setLosingTrades(losses);
        result.setWinRate(closedCount > 0 ? (double) wins / closedCount : 0);
        result.setProfitLoss(totalProfitLoss);
        result.setTotalReturn(totalReturnPct);
        result.setMaxDrawdown(maxDrawdownPct);
        result.setOpenPositionCount(record.getOpenPositions().size());
        result.setFinalCapital(equity);
        result.setTotalFees(totalFees);
        result.setSignals(signals);

        log.info("Backtest result: initialCapital={}, finalCapital={}, totalReturn={}%, totalFees={}, trades={}",
                INITIAL_CAPITAL, equity, String.format("%.2f", totalReturnPct), totalFees, closedCount);

        return result;
    }

    private double calcBuyFees(double amount) {
        double commission = Math.max(amount * COMMISSION_RATE, MIN_COMMISSION);
        double transferFee = amount * TRANSFER_FEE_RATE;
        return commission + transferFee;
    }

    private double calcSellFees(double amount) {
        double stampDuty = amount * STAMP_DUTY_RATE;
        double commission = Math.max(amount * COMMISSION_RATE, MIN_COMMISSION);
        double transferFee = amount * TRANSFER_FEE_RATE;
        return stampDuty + commission + transferFee;
    }

    private BacktestResult.BacktestSignal createSignal(Trade trade, BarSeries series, String type) {
        BacktestResult.BacktestSignal signal = new BacktestResult.BacktestSignal();
        signal.setTrade_date(series.getBar(trade.getIndex()).getEndTime().atZone(ZoneId.systemDefault()).toLocalDate().toString());
        signal.setType(type);
        signal.setPrice(trade.getPricePerAsset().doubleValue());
        signal.setIndex(trade.getIndex());
        return signal;
    }

    private BarSeries convertToBarSeries(List<DailyQuote> quotes) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        List<DailyQuote> sorted = new ArrayList<>(quotes);
        sorted.sort((a, b) -> a.getTradeDate().compareTo(b.getTradeDate()));

        for (DailyQuote q : sorted) {
            ZonedDateTime zdt = ZonedDateTime.of(
                    java.time.LocalDate.parse(q.getTradeDate(), formatter),
                    java.time.LocalTime.of(15, 0),
                    ZoneId.systemDefault()
            );
            Instant endTime = zdt.toInstant();

            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(q.getOpen())
                    .highPrice(q.getHigh())
                    .lowPrice(q.getLow())
                    .closePrice(q.getClose())
                    .volume(q.getVol())
                    .build());
        }
        return series;
    }

    private org.ta4j.core.Strategy buildDefaultStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
        SMAIndicator longSma = new SMAIndicator(closePrice, 20);

        Rule entryRule = new CrossedUpIndicatorRule(shortSma, longSma);
        Rule exitRule = new CrossedDownIndicatorRule(shortSma, longSma);

        return new BaseStrategy(entryRule, exitRule);
    }
}
