package com.tradingx.service;

import com.tradingx.model.NotFoundException;
import com.tradingx.model.Strategy;
import com.tradingx.model.User;
import com.tradingx.model.ValidationException;
import com.tradingx.repository.StrategyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyService {

    private final StrategyRepository strategyRepository;
    private final StrategyCompiler strategyCompiler;
    private final VisualStrategyBuilder visualStrategyBuilder;

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
        return listByUser(currentUser.getUsername());
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
        strategy.setCreatedBy(currentUser.getUsername());
        validateStrategy(strategy);
        return strategyRepository.save(strategy);
    }

    public Strategy update(Long id, Strategy updates) {
        Strategy existing = getById(id);
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
        }
        return strategyRepository.save(existing);
    }

    public void delete(Long id) {
        if (!strategyRepository.existsById(id)) {
            throw new NotFoundException("策略不存在: " + id);
        }
        strategyRepository.deleteById(id);
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
