$code = @'
package com.tradingx.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.StochasticOscillatorDIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;

public class PatternMomentumStrategy {

    public static Strategy buildStrategy(BarSeries series) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        HighPriceIndicator highPrice = new HighPriceIndicator(series);
        LowPriceIndicator lowPrice = new LowPriceIndicator(series);

        SMAIndicator ma5 = new SMAIndicator(closePrice, 5);
        SMAIndicator ma10 = new SMAIndicator(closePrice, 10);
        SMAIndicator ma20 = new SMAIndicator(closePrice, 20);

        RSIIndicator rsi6 = new RSIIndicator(closePrice, 6);
        RSIIndicator rsi14 = new RSIIndicator(closePrice, 14);

        StochasticOscillatorKIndicator stochK = new StochasticOscillatorKIndicator(series, 14);
        StochasticOscillatorDIndicator stochD = new StochasticOscillatorDIndicator(stochK);

        Rule entryRule = new UnderIndicatorRule(closePrice, ma5)
                .and(new UnderIndicatorRule(closePrice, ma10))
                .and(new CrossedDownIndicatorRule(rsi6, 39.1))
                .and(new UnderIndicatorRule(stochK, 31.5));

        Rule exitRule = new OverIndicatorRule(closePrice, ma5)
                .and(new OverIndicatorRule(closePrice, ma10))
                .and(new CrossedUpIndicatorRule(rsi6, 60.4))
                .and(new OverIndicatorRule(stochK, 61.8));

        return new BaseStrategy("PatternMomentumStrategy", entryRule, exitRule, 60);
    }
}
'@

$body = @{
    code = $code
} | ConvertTo-Json -Depth 10

$headers = @{
    "Content-Type" = "application/json"
}

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/strategy/validate-code" -Method POST -Body $body -ContentType "application/json" -Headers $headers
    $response | ConvertTo-Json -Depth 5
} catch {
    Write-Host "Error: $_"
}
