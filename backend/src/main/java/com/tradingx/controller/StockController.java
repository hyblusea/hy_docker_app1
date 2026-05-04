package com.tradingx.controller;

import com.tradingx.model.DailyQuery;
import com.tradingx.model.DailyQuote;
import com.tradingx.model.R;
import com.tradingx.model.StockBasic;
import com.tradingx.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 股票数据接口控制器
 * <p>
 * 提供股票列表、搜索、日线行情查询等 API 接口
 * </p>
 */
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    /**
     * 获取全部股票列表
     *
     * @return 股票基础信息列表（包含 5512 只 A 股）
     */
    @GetMapping("/list")
    public R<List<StockBasic>> getStockList() {
        return R.ok(stockService.getStockList());
    }

    /**
     * 按关键词搜索股票
     *
     * @param keyword 搜索关键词（支持股票代码、名称、拼音简拼）
     * @return 匹配的股票列表
     */
    @GetMapping("/search")
    public R<List<StockBasic>> searchStock(@RequestParam String keyword) {
        return R.ok(stockService.searchStock(keyword));
    }

    /**
     * 查询股票日线行情数据
     * <p>
     * 通过 Tushare 接口获取指定股票在指定日期范围内的历史日线行情
     * </p>
     *
     * @param query 日线查询条件（包含 tsCode、startDate、endDate 等参数）
     * @return 日线行情列表（按日期倒序排列）
     */
    @PostMapping("/daily")
    public R<List<DailyQuote>> queryDaily(@RequestBody DailyQuery query) {
        return R.ok(stockService.queryDaily(query));
    }
}
