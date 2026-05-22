package com.tradingx.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class StrategyCompilerCache {

    private static final Logger log = LoggerFactory.getLogger(StrategyCompilerCache.class);

    private final StrategyCompiler strategyCompiler;
    private final VisualStrategyBuilder visualStrategyBuilder;

    private final Cache<Long, CompiledStrategy> cache = Caffeine.newBuilder()
            .maximumSize(200)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    public StrategyCompilerCache(StrategyCompiler strategyCompiler,
                                  VisualStrategyBuilder visualStrategyBuilder) {
        this.strategyCompiler = strategyCompiler;
        this.visualStrategyBuilder = visualStrategyBuilder;
    }

    public CompiledStrategy getOrCompile(Long strategyId, String code, String language) {
        return cache.get(strategyId, k -> {
            log.info("缓存未命中, 开始编译: strategyId={}, lang={}", strategyId, language);
            if ("visual".equalsIgnoreCase(language)) {
                return visualStrategyBuilder.parse(code);
            } else {
                return strategyCompiler.compile(code);
            }
        });
    }

    public void evict(Long strategyId) {
        log.info("驱逐缓存: strategyId={}", strategyId);
        cache.invalidate(strategyId);
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    // 可选：暴露统计信息，方便监控
    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        return cache.stats();
    }
}
