package com.tradingx.controller;

import com.tradingx.model.BacktestRequest;
import com.tradingx.model.BacktestResult;
import com.tradingx.model.R;
import com.tradingx.service.BacktestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final BacktestService backtestService;

    @PostMapping
    public R<BacktestResult> runBacktest(@RequestBody BacktestRequest request) {
        return R.ok(backtestService.runBacktest(request.getStrategyId(), request.getQuotes()));
    }
}
