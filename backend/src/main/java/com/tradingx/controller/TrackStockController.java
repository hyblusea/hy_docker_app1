package com.tradingx.controller;

import com.tradingx.model.R;
import com.tradingx.model.TrackStockVO;
import com.tradingx.model.User;
import com.tradingx.service.TrackStockService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/track")
public class TrackStockController {

    private final TrackStockService trackStockService;

    public TrackStockController(TrackStockService trackStockService) {
        this.trackStockService = trackStockService;
    }

    @PostMapping("/add")
    public R<Boolean> addTrack(@RequestBody Map<String, Object> body, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return R.fail("未登录");

        String tsCode = (String) body.get("ts_code");
        String stockName = (String) body.get("stock_name");
        Long strategyId = ((Number) body.get("strategy_id")).longValue();
        String strategyName = (String) body.get("strategy_name");

        boolean added = trackStockService.addTrack(user.getUsername(), tsCode, stockName, strategyId, strategyName);
        if (!added) {
            return R.fail("该股票已在跟踪列表中");
        }
        return R.ok(true);
    }

    @GetMapping("/list")
    public R<List<TrackStockVO>> listTracks(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return R.fail("未登录");
        return R.ok(trackStockService.listTracksWithChange(user.getUsername()));
    }

    @DeleteMapping("/{id}")
    public R<Boolean> removeTrack(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) return R.fail("未登录");
        trackStockService.removeTrack(id, user.getUsername());
        return R.ok(true);
    }
}
