package com.tradingx.service;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

@FunctionalInterface
public interface CompiledStrategy {
    Strategy create(BarSeries series);
}
