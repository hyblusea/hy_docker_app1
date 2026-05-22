package com.tradingx.rules;

import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;

public class MaxTradeBarCountRule implements Rule {

    private final int maxBarCount;

    public MaxTradeBarCountRule(int maxBarCount) {
        if (maxBarCount <= 0) {
            throw new IllegalArgumentException("maxBarCount must be positive, got: " + maxBarCount);
        }
        this.maxBarCount = maxBarCount;
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        if (tradingRecord == null) {
            return false;
        }
        var currentPosition = tradingRecord.getCurrentPosition();
        if (!currentPosition.isOpened()) {
            return false;
        }
        int entryIndex = currentPosition.getEntry().getIndex();
        return (index - entryIndex) >= maxBarCount;
    }
}
