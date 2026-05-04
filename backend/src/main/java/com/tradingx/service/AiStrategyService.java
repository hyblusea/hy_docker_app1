package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiStrategyService {

    private final StrategyCompiler strategyCompiler;
    private final ObjectMapper objectMapper;

    @Value("${ai.siliconflow.api-key:}")
    private String apiKey;

    @Value("${ai.siliconflow.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    @Value("${ai.siliconflow.model:Qwen/Qwen2.5-7B-Instruct}")
    private String model;

    private static final int MAX_RETRIES = 2;

    private static final String SYSTEM_PROMPT = """
            你是一个量化交易策略代码生成助手。用户会描述买入和卖出策略，你需要生成符合以下规范的Java代码。

            ## 严格规范

            1. 代码必须包含一个 public 类，类名使用 PascalCase 命名（如 RsiStrategy）
            2. 类必须包含方法: public Strategy buildStrategy(BarSeries series)
            3. 返回类型是 org.ta4j.core.Strategy
            4. 只允许使用以下 import:
               - org.ta4j.core.*
               - org.ta4j.core.indicators.*
               - org.ta4j.core.indicators.helpers.*
               - org.ta4j.core.indicators.statistics.*
               - org.ta4j.core.indicators.volume.*
               - org.ta4j.core.indicators.ichimoku.*
               - org.ta4j.core.indicators.bollinger.*
               - org.ta4j.core.indicators.keltner.*
               - org.ta4j.core.rules.*
               - com.tradingx.rules.MaxTradeBarCountRule
            5. 不要使用任何不在上述列表中的类或方法
            6. 如果用户描述的策略需要本系统不支持的指标或规则，请在策略名称后标注"[部分不支持]"，并用最接近的已有指标替代

            ## 可用指标（部分常用）
            - ClosePriceIndicator(series)
            - SMAIndicator(indicator, barCount)
            - EMAIndicator(indicator, barCount)
            - RSIIndicator(indicator, barCount)
            - MACDIndicator(indicator, shortBarCount, longBarCount)
            - BollingerBandsUpperIndicator(BollingerBandsMiddleIndicator)
            - BollingerBandsLowerIndicator(BollingerBandsMiddleIndicator)
            - BollingerBandsMiddleIndicator(SMAIndicator, StandardDeviationIndicator)
            - ATRIndicator(series, barCount)
            - CCIIndicator(series, barCount)
            - VolumeIndicator(series)
            - IchimokuTenkanSenIndicator(series), IchimokuKijunSenIndicator(series)
            - OBVIndicator(series), CMFIndicator(series, barCount)
            - StandardDeviationIndicator(indicator, barCount)

            ## 可用规则
            - CrossedUpRule(indicator1, indicator2)
            - CrossedDownRule(indicator1, indicator2)
            - OverIndicatorRule(indicator1, indicator2)
            - UnderIndicatorRule(indicator1, indicator2)
            - IsRisingRule(indicator, barCount)
            - IsFallingRule(indicator, barCount)
            - StopLossRule(series, lossPercentage)
            - StopGainRule(series, gainPercentage)
            - TrailingStopLossRule(series, lossPercentage)
            - MaxTradeBarCountRule(maxBarCount)
            - BooleanIndicatorRule(indicator)
            - InPipeRule(indicator, lower, upper)

            ## 输出格式（严格遵守）
            第一行输出策略名称建议（不含代码），然后空一行，接着输出完整的Java代码。
            格式如下：
            策略名称:XXX策略

            ```java
            import org.ta4j.core.*;
            // ... 完整代码
            ```
            """;

    public AiGenerateResult generate(String buyDesc, String sellDesc) {
        String userPrompt = buildUserPrompt(buyDesc, sellDesc);
        AiGenerateResult result = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String aiResponse = callAiApi(userPrompt, attempt > 0 ? result : null);
                result = parseAiResponse(aiResponse);

                if (result.code == null || result.code.isBlank()) {
                    log.warn("AI返回结果中未包含有效代码, 重试 {}/{}", attempt + 1, MAX_RETRIES);
                    continue;
                }

                String compileError = strategyCompiler.compileCheck(result.code);
                if (compileError == null) {
                    result.valid = true;
                    log.info("AI生成策略编译通过: {}", result.suggestedName);
                    return result;
                }

                result.valid = false;
                result.compileError = compileError;
                log.warn("AI生成策略编译失败(尝试 {}/{}): {}", attempt + 1, MAX_RETRIES + 1, compileError);

                if (attempt < MAX_RETRIES) {
                    log.info("将编译错误反馈给AI进行重试...");
                }
            } catch (Exception e) {
                log.error("AI策略生成失败(尝试 {}): {}", attempt + 1, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    result = new AiGenerateResult();
                    result.valid = false;
                    result.compileError = "AI生成失败: " + e.getMessage();
                }
            }
        }

        return result != null ? result : new AiGenerateResult();
    }

    private String buildUserPrompt(String buyDesc, String sellDesc) {
        return "请根据以下策略描述生成Java代码：\n\n" +
                "买入策略: " + buyDesc + "\n\n" +
                "卖出策略: " + sellDesc;
    }

    private String callAiApi(String userPrompt, AiGenerateResult previousResult) throws Exception {
        RestTemplate restTemplate = new RestTemplate();

        StringBuilder messages = new StringBuilder();
        messages.append("{\"role\":\"system\",\"content\":")
                .append(objectMapper.writeValueAsString(SYSTEM_PROMPT))
                .append("}");

        messages.append(",{\"role\":\"user\",\"content\":")
                .append(objectMapper.writeValueAsString(userPrompt))
                .append("}");

        if (previousResult != null && previousResult.code != null && previousResult.compileError != null) {
            String assistantContent = "策略名称:" + (previousResult.suggestedName != null ? previousResult.suggestedName : "未命名") + "\n\n```java\n" + previousResult.code + "\n```";
            messages.append(",{\"role\":\"assistant\",\"content\":")
                    .append(objectMapper.writeValueAsString(assistantContent))
                    .append("}");

            String retryContent = "上一次生成的代码编译失败，错误信息:\n" + previousResult.compileError + "\n\n请修复代码并重新生成。确保只使用系统支持的指标和规则。";
            messages.append(",{\"role\":\"user\",\"content\":")
                    .append(objectMapper.writeValueAsString(retryContent))
                    .append("}");
        }

        String requestBody = "{\"model\":\"" + model + "\",\"messages\":[" + messages + "],\"max_tokens\":4096,\"temperature\":0.7}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/chat/completions",
                HttpMethod.POST,
                entity,
                String.class
        );

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("choices").path(0).path("message").path("content").asText("");
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

        result.suggestedName = nameLine;
        result.code = code;
        return result;
    }

    public static class AiGenerateResult {
        public String suggestedName;
        public String code;
        public boolean valid;
        public String compileError;
    }
}
