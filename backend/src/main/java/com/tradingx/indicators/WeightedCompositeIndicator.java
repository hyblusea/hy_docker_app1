package com.tradingx.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

import java.util.ArrayList;
import java.util.List;

public class WeightedCompositeIndicator extends AbstractIndicator<Num> {

    private final List<Indicator<Num>> factorIndicators;
    private final List<Double> weights;
    private final int lookbackPeriod;

    private final List<SMAIndicator> smaIndicators;
    private final List<StandardDeviationIndicator> stdIndicators;

    public WeightedCompositeIndicator(BarSeries series,
                                       List<Indicator<Num>> factorIndicators,
                                       List<Double> weights,
                                       int lookbackPeriod) {
        super(series);
        if (factorIndicators.size() != weights.size()) {
            throw new IllegalArgumentException("因子数量与权重数量不匹配");
        }
        this.factorIndicators = new ArrayList<>(factorIndicators);
        this.weights = new ArrayList<>(weights);
        this.lookbackPeriod = lookbackPeriod;

        this.smaIndicators = new ArrayList<>();
        this.stdIndicators = new ArrayList<>();
        for (Indicator<Num> factor : factorIndicators) {
            smaIndicators.add(new SMAIndicator(factor, lookbackPeriod));
            stdIndicators.add(new StandardDeviationIndicator(factor, lookbackPeriod));
        }
    }

    @Override
    public Num getValue(int index) {
        Num composite = numOf(0);

        for (int i = 0; i < factorIndicators.size(); i++) {
            Num factorValue = factorIndicators.get(i).getValue(index);
            Num mean = smaIndicators.get(i).getValue(index);
            Num std = stdIndicators.get(i).getValue(index);

            Num zScore;
            if (std.isZero() || std.isNaN()) {
                zScore = numOf(0);
            } else {
                zScore = factorValue.minus(mean).dividedBy(std);
            }

            if (zScore.isNaN()) {
                zScore = numOf(0);
            }

            composite = composite.plus(zScore.multipliedBy(numOf(weights.get(i))));
        }

        return composite;
    }

    @Override
    public int getCountOfUnstableBars() {
        return lookbackPeriod;
    }

    private Num numOf(double value) {
        return getBarSeries().numFactory().numOf(value);
    }
}
