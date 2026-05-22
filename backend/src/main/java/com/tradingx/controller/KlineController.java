package com.tradingx.controller;

import com.tradingx.model.DailyQuote;
import com.tradingx.model.R;
import com.tradingx.service.KlineService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kline")
public class KlineController {

    private final KlineService klineService;

    public KlineController(KlineService klineService) {
        this.klineService = klineService;
    }

    @GetMapping("/data")
    public R<List<DailyQuote>> getKlineData(
            @RequestParam String tsCode,
            @RequestParam(defaultValue = "day") String period) {
        return R.ok(klineService.getKlineData(tsCode, period));
    }
}
