package com.tradingx.service;

import com.tradingx.client.DeepSeekWebClient;
import com.tradingx.client.MimoWebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiStrategyService {

    private final StrategyCompiler strategyCompiler;
    private final MimoWebClient mimoClient;
    private final DeepSeekWebClient deepSeekClient;

    private static final String SYSTEM_PROMPT = """
            基于 ta4j v0.22.6 库编写量化交易策略类代码,注意版本必须是v0.22.6,不能使用其他版本的库否则我编译会报错：

            生成的java代码必须遵守的规则:
            1. 包名必须是 com.tradingx.strategy
            2. 类名必须以 Strategy 结尾
            3. 必须使用ta4j v0.22.6库中已有的策略、指标、规则类来完成，
            4. 对于使用到的 ta4j对象，必须与官方应对版本的源码核对，源码库地址https://github.com/ta4j/ta4j/tree/0.22.6 
            5. 否则如需自定义方法实现的，所有方法都必须在类中实现
            6. 必须有方法 public Strategy buildStrategy(BarSeries series)
            其他规则:
            Ta4j v0.22.6 关键包路径（必须严格遵守，否则编译报错）:
            - org.ta4j.core — Strategy, BaseStrategy, Rule, BarSeries, Num, Indicator
            - org.ta4j.core.indicators — RSIIndicator, MACDIndicator, StochasticOscillatorKIndicator, StochasticOscillatorDIndicator, StochasticIndicator, ClosePriceIndicator, HighPriceIndicator, LowPriceIndicator, OpenPriceIndicator, VolumeIndicator, PreviousValueIndicator, DifferenceIndicator, CumulatedGainsIndicator, CumulatedLossesIndicator
            - org.ta4j.core.indicators.averages — SMAIndicator, EMAIndicator, WMAIndicator, MMAIndicator (注意：SMAIndicator和EMAIndicator在averages子包，不在indicators包！)
            - org.ta4j.core.indicators.adx — ADXIndicator, MinusDIIndicator, PlusDIIndicator
            - org.ta4j.core.indicators.bollinger — BollingerBandsLowerIndicator, BollingerBandsMiddleIndicator, BollingerBandsUpperIndicator, BollingerBandsWidthIndicator
            - org.ta4j.core.indicators.helpers — ClosePriceIndicator, HighPriceIndicator, LowPriceIndicator, OpenPriceIndicator, VolumeIndicator, PreviousValueIndicator, DifferenceIndicator
            - org.ta4j.core.rules — CrossedUpIndicatorRule, CrossedDownIndicatorRule, OverIndicatorRule, UnderIndicatorRule, BooleanRuleRule, NotRule, IsRisingRule, IsFallingRule

            KDJ正确用法（StochasticOscillator在org.ta4j.core.indicators包）:
                StochasticOscillatorKIndicator kLine = new StochasticOscillatorKIndicator(series, 9);
                StochasticOscillatorDIndicator dLine = new StochasticOscillatorDIndicator(kLine);

            均线正确用法（SMAIndicator在org.ta4j.core.indicators.averages包）:
                import org.ta4j.core.indicators.averages.SMAIndicator;
                import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
                ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
                SMAIndicator sma5 = new SMAIndicator(closePrice, 5);

            输出格式: 第一行"策略名称:XXX"，空一行，然后用 ```java 代码块输出完整 java 类代码```
            只输出以上要求的内容。
            """;

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");
    private static final String PACKAGE_NAME = "com.tradingx.strategy";
    private static final int MAX_RETRIES = 10;
    private static final Random random = new Random();

    public AiStrategyService(StrategyCompiler strategyCompiler, 
                            @Autowired(required = false) MimoWebClient mimoClient,
                            @Autowired(required = false) DeepSeekWebClient deepSeekClient) {
        this.strategyCompiler = strategyCompiler;
        this.mimoClient = mimoClient;
        this.deepSeekClient = deepSeekClient;
        
        if (mimoClient != null) {
            log.info("AI Strategy Service initialized with MiMo client");
        } else if (deepSeekClient != null) {
            log.info("AI Strategy Service initialized with DeepSeek client");
        } else {
            log.warn("AI Strategy Service initialized without any AI client");
        }
    }

    public interface RetryCallback {
        void onRetry(int retryCount, int maxRetries, String error);
        boolean isCancelled();
    }

    public AiGenerateResult generate(String buyDesc, String sellDesc) {
        return generate(buyDesc, sellDesc, null, null);
    }

    public AiGenerateResult generate(String buyDesc, String sellDesc, Consumer<String> onThinking) {
        return generate(buyDesc, sellDesc, onThinking, null);
    }

    public AiGenerateResult generate(String buyDesc, String sellDesc, Consumer<String> onThinking, RetryCallback retryCallback) {
        String lastError = null;
        String lastCode = null;
        int retryCount = 0;

        while (retryCount <= MAX_RETRIES) {
            if (retryCallback != null && retryCallback.isCancelled()) {
                log.info("AI generation cancelled by user");
                AiGenerateResult result = new AiGenerateResult();
                result.valid = false;
                result.compileError = "用户取消了生成";
                return result;
            }

            String userPrompt;
            if (retryCount == 0) {
                userPrompt = buildUserPrompt(buyDesc, sellDesc);
            } else {
                userPrompt = buildRetryPrompt(buyDesc, sellDesc, lastError, lastCode);
            }

            try {
                String aiResponse;
                
                if (mimoClient != null) {
                    log.debug("Using MiMo client for strategy generation (attempt {})", retryCount + 1);
                    aiResponse = mimoClient.chat(userPrompt, onThinking);
                } else if (deepSeekClient != null) {
                    log.debug("Using DeepSeek client for strategy generation (attempt {})", retryCount + 1);
                    aiResponse = deepSeekClient.chat(userPrompt, onThinking);
                } else {
                    throw new IllegalStateException("No AI client configured (neither MiMo nor DeepSeek)");
                }
                
                AiGenerateResult result = parseAiResponse(aiResponse);

                if (result.code == null || result.code.isBlank()) {
                    lastError = "AI返回结果中未包含有效代码";
                    lastCode = null;
                } else {
                    String compileError = strategyCompiler.compileCheck(result.code);
                    if (compileError == null) {
                        result.valid = true;
                        log.info("AI生成策略编译通过: {} (重试{}次)", result.suggestedName, retryCount);
                        return result;
                    }
                    lastError = compileError;
                    lastCode = result.code;
                    result.valid = false;
                    result.compileError = compileError;
                }

                if (retryCount < MAX_RETRIES) {
                    if (retryCallback != null) {
                        retryCallback.onRetry(retryCount + 1, MAX_RETRIES, lastError);
                    }
                    int sleepMs = 3000 + random.nextInt(2000);
                    log.info("编译失败，{}秒后重试 ({}/{})", sleepMs / 1000, retryCount + 1, MAX_RETRIES);
                    Thread.sleep(sleepMs);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("AI generation interrupted");
                AiGenerateResult result = new AiGenerateResult();
                result.valid = false;
                result.compileError = "生成被中断";
                return result;
            } catch (Exception e) {
                log.error("AI策略生成失败: {}", e.getMessage());
                lastError = "AI生成失败: " + e.getMessage();
                lastCode = null;
                
                if (retryCount < MAX_RETRIES) {
                    if (retryCallback != null) {
                        retryCallback.onRetry(retryCount + 1, MAX_RETRIES, lastError);
                    }
                    try {
                        int sleepMs = 3000 + random.nextInt(2000);
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        AiGenerateResult result = new AiGenerateResult();
                        result.valid = false;
                        result.compileError = "生成被中断";
                        return result;
                    }
                }
            }
            
            retryCount++;
        }

        AiGenerateResult result = new AiGenerateResult();
        result.valid = false;
        result.compileError = lastError != null ? lastError : "重试次数已达上限";
        result.code = lastCode;
        return result;
    }

    private String buildUserPrompt(String buyDesc, String sellDesc) {
        return SYSTEM_PROMPT + "\n\n请根据以下策略描述生成Java代码：\n\n" +
                "买入策略: " + buyDesc + "\n\n" +
                "卖出策略: " + sellDesc;
    }

    private String buildRetryPrompt(String buyDesc, String sellDesc, String lastError, String lastCode) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(SYSTEM_PROMPT).append("\n\n");
        prompt.append("【重要】这是重试请求，之前生成的代码编译失败，请仔细检查后重新生成。\n\n");
        prompt.append("原始策略描述：\n");
        prompt.append("买入策略: ").append(buyDesc).append("\n\n");
        prompt.append("卖出策略: ").append(sellDesc).append("\n\n");
        prompt.append("--- 以下是之前的错误信息 ---\n");
        prompt.append(lastError != null ? lastError : "未知错误").append("\n\n");
        prompt.append("--- 请特别注意 ---\n");
        prompt.append("1. 确认使用的是 Ta4j v0.22.6 版本，不是其他版本\n");
        prompt.append("2. 检查所有 import 语句的包路径是否正确\n");
        prompt.append("3. 检查所有类和方法的构造函数参数是否正确\n");
        prompt.append("4. 给出完整的 Java 类代码，不要省略任何部分\n\n");
        if (lastCode != null && !lastCode.isBlank()) {
            prompt.append("--- 之前生成的代码（仅供参考，请修正错误） ---\n");
            prompt.append(lastCode).append("\n\n");
        }
        prompt.append("请重新生成修正后的完整代码：");
        return prompt.toString();
    }

    private AiGenerateResult parseAiResponse(String aiResponse) {
        AiGenerateResult result = new AiGenerateResult();

        String nameLine = null;
        String code = null;

        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("策略名称:") || trimmed.startsWith("策略名称：")) {
                nameLine = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (nameLine.startsWith(":") || nameLine.startsWith("：")) {
                    nameLine = nameLine.substring(1).trim();
                }
            }
        }

        int codeStart = aiResponse.indexOf("```java");
        if (codeStart == -1) {
            codeStart = aiResponse.indexOf("```");
        }
        if (codeStart != -1) {
            int codeContentStart = aiResponse.indexOf('\n', codeStart) + 1;
            int codeEnd = aiResponse.indexOf("```", codeContentStart);
            if (codeEnd != -1) {
                code = aiResponse.substring(codeContentStart, codeEnd).trim();
            }
        }

        if (code == null || code.isBlank()) {
            code = aiResponse;
        }

        code = ensureCode(code);

        result.suggestedName = nameLine;
        result.code = code;
        return result;
    }

    private String ensureCode(String code) {
        if (!code.contains("package " + PACKAGE_NAME)) {
            code = "package " + PACKAGE_NAME + ";\n\n" + code;
        }

        code = code.replaceAll("(?m)^(\\s*)class\\s+(\\w+)", "$1public class $2");

        return code;
    }

    public static class AiGenerateResult {
        public String suggestedName;
        public String code;
        public boolean valid;
        public String compileError;
    }
}
