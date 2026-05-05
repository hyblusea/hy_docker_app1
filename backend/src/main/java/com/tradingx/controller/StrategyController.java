package com.tradingx.controller;

import com.tradingx.model.R;
import com.tradingx.model.Strategy;
import com.tradingx.model.User;
import com.tradingx.service.AiStrategyService;
import com.tradingx.service.StrategyCompiler;
import com.tradingx.service.StrategyService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/strategy")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService strategyService;
    private final AiStrategyService aiStrategyService;
    private final StrategyCompiler strategyCompiler;

    @GetMapping("/list")
    public R<List<Strategy>> list(HttpSession session) {
        User user = getCurrentUser(session);
        return R.ok(strategyService.listVisible(user));
    }

    @GetMapping("/list-valid")
    public R<List<Strategy>> listValid(HttpSession session) {
        User user = getCurrentUser(session);
        List<Strategy> all = strategyService.listVisible(user);
        List<Strategy> valid = all.stream()
                .filter(s -> s.getValid() == null || s.getValid())
                .collect(Collectors.toList());
        return R.ok(valid);
    }

    @GetMapping("/{id:\\d+}")
    public R<Strategy> getById(@PathVariable Long id) {
        return R.ok(strategyService.getById(id));
    }

    @PostMapping
    public R<Strategy> create(@RequestBody Strategy strategy, HttpSession session) {
        User user = getCurrentUser(session);
        return R.ok(strategyService.create(strategy, user));
    }

    @PutMapping("/{id:\\d+}")
    public R<Strategy> update(@PathVariable Long id, @RequestBody Strategy strategy) {
        return R.ok(strategyService.update(id, strategy));
    }

    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        strategyService.delete(id);
        return R.ok(null);
    }

    @PostMapping("/validate-code")
    public R<Map<String, Object>> validateCode(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            return R.fail("代码不能为空");
        }
        String error = strategyCompiler.compileCheck(code);
        boolean valid = error == null;
        return R.ok(Map.of(
                "valid", valid,
                "compileError", error != null ? error : ""
        ));
    }

    @PostMapping("/ai-generate")
    public R<Map<String, Object>> aiGenerate(@RequestBody Map<String, String> request) {
        String buyDesc = request.get("buyDesc");
        String sellDesc = request.get("sellDesc");
        if (buyDesc == null || buyDesc.isBlank() || sellDesc == null || sellDesc.isBlank()) {
            return R.fail("请输入买入和卖出策略描述");
        }
        AiStrategyService.AiGenerateResult result = aiStrategyService.generate(buyDesc, sellDesc);
        return R.ok(Map.of(
                "suggestedName", result.suggestedName != null ? result.suggestedName : "",
                "code", result.code != null ? result.code : "",
                "valid", result.valid,
                "compileError", result.compileError != null ? result.compileError : ""
        ));
    }

    @PostMapping(value = "/ai-generate-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiGenerateStream(@RequestBody Map<String, String> request, HttpSession session) {
        String buyDesc = request.get("buyDesc");
        String sellDesc = request.get("sellDesc");

        SseEmitter emitter = new SseEmitter(300000L);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                if (buyDesc == null || buyDesc.isBlank() || sellDesc == null || sellDesc.isBlank()) {
                    emitter.send(SseEmitter.event().name("error").data("请输入买入和卖出策略描述"));
                    emitter.complete();
                    return;
                }

                AiStrategyService.AiGenerateResult result = aiStrategyService.generate(buyDesc, sellDesc, delta -> {
                    try {
                        emitter.send(SseEmitter.event().name("thinking").data(delta));
                    } catch (IOException ignored) {
                    }
                });

                emitter.send(SseEmitter.event().name("result").data(Map.of(
                        "suggestedName", result.suggestedName != null ? result.suggestedName : "",
                        "code", result.code != null ? result.code : "",
                        "valid", result.valid,
                        "compileError", result.compileError != null ? result.compileError : ""
                )));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("AI生成失败: " + e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            } finally {
                executor.shutdown();
            }
        });

        return emitter;
    }

    private User getCurrentUser(HttpSession session) {
        return (User) session.getAttribute("user");
    }
}
