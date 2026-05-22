package com.tradingx.service;

import com.tradingx.model.NotFoundException;
import com.tradingx.model.Strategy;
import com.tradingx.model.User;
import com.tradingx.model.ValidationException;
import com.tradingx.repository.StrategyRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StrategyService {

    private static final Logger log = LoggerFactory.getLogger(StrategyService.class);

    private final StrategyRepository strategyRepository;
    private final StrategyCompiler strategyCompiler;
    private final VisualStrategyBuilder visualStrategyBuilder;
    private final StrategyCompilerCache strategyCompilerCache;  // 改动1: 新增注入

    public StrategyService(StrategyRepository strategyRepository,
                           StrategyCompiler strategyCompiler,
                           VisualStrategyBuilder visualStrategyBuilder,
                           StrategyCompilerCache strategyCompilerCache) {  // 改动1: 构造函数加参数
        this.strategyRepository = strategyRepository;
        this.strategyCompiler = strategyCompiler;
        this.visualStrategyBuilder = visualStrategyBuilder;
        this.strategyCompilerCache = strategyCompilerCache;
    }

    @PostConstruct
    public void init() {
        List<Strategy> all = strategyRepository.findAll();
        for (Strategy s : all) {
            if (s.getValid() == null) {
                validateStrategy(s);
                strategyRepository.save(s);
                log.info("策略 [{}] 编译检查: {}", s.getName(), s.getValid() ? "有效" : "无效 - " + s.getCompileError());
            }
        }
    }

    public List<Strategy> listAll() {
        return strategyRepository.findAll();
    }

    public List<Strategy> listByUser(String username) {
        return strategyRepository.findByCreatedBy(username);
    }

    public List<Strategy> listVisible(User currentUser) {
        if (currentUser != null && "root".equals(currentUser.getRole())) {
            return listAll();
        }
        return strategyRepository.findVisibleByUser(currentUser.getUsername());
    }

    public List<Strategy> listValid() {
        return strategyRepository.findValidStrategies();
    }

    public Strategy getById(Long id) {
        return strategyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("策略不存在: " + id));
    }

    public Strategy create(Strategy strategy, User currentUser) {
        if (strategy.getName() == null || strategy.getName().isBlank()) {
            throw new ValidationException("策略名称不能为空");
        }
        if (strategy.getLanguage() == null || strategy.getLanguage().isBlank()) {
            throw new ValidationException("策略语言不能为空");
        }
        if (currentUser != null) {
            strategy.setCreatedBy(currentUser.getUsername());
            strategy.setCreatedByRole(currentUser.getRole());
        }
        validateStrategy(strategy);
        return strategyRepository.save(strategy);
    }

    public Strategy update(Long id, Strategy updates, User currentUser) {
        Strategy existing = getById(id);
        if (!isOwner(existing, currentUser)) {
            throw new ValidationException("只能修改自己创建的策略");
        }
        boolean needRevalidate = false;

        if (updates.getName() != null) {
            existing.setName(updates.getName());
        }
        if (updates.getLanguage() != null && !updates.getLanguage().equals(existing.getLanguage())) {
            existing.setLanguage(updates.getLanguage());
            needRevalidate = true;
        }
        if (updates.getCode() != null) {
            existing.setCode(updates.getCode());
            needRevalidate = true;
        }
        if (needRevalidate) {
            validateStrategy(existing);
            strategyCompilerCache.evict(id);  // 改动2: 代码或语言变更时驱逐缓存
        }

        return strategyRepository.save(existing);
    }

    public void delete(Long id, User currentUser) {
        Strategy existing = getById(id);
        if (!isOwner(existing, currentUser)) {
            throw new ValidationException("只能删除自己创建的策略");
        }
        strategyCompilerCache.evict(id);  // 改动3: 删除时也驱逐缓存
        strategyRepository.delete(existing);
    }

    private boolean isOwner(Strategy strategy, User currentUser) {
        if (currentUser == null) return false;
        if ("root".equals(currentUser.getRole())) return true;
        return currentUser.getUsername().equals(strategy.getCreatedBy());
    }

    private void validateStrategy(Strategy strategy) {
        if (strategy.getCode() != null && !strategy.getCode().isBlank()) {
            if ("visual".equalsIgnoreCase(strategy.getLanguage())) {
                String error = visualStrategyBuilder.validate(strategy.getCode());
                strategy.setValid(error == null);
                strategy.setCompileError(error);
            } else if ("java".equalsIgnoreCase(strategy.getLanguage())) {
                String error = strategyCompiler.compileCheck(strategy.getCode());
                strategy.setValid(error == null);
                strategy.setCompileError(error);
            } else {
                strategy.setValid(true);
                strategy.setCompileError(null);
            }
        } else {
            strategy.setValid(true);
            strategy.setCompileError(null);
        }
    }
}
