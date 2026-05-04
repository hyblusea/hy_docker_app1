package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiStrategyService {

    private final StrategyCompiler strategyCompiler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiStrategyService(StrategyCompiler strategyCompiler) {
        this.strategyCompiler = strategyCompiler;
    }

    @Value("${ai.siliconflow.api-key:}")
    private String apiKey;

    @Value("${ai.siliconflow.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    @Value("${ai.siliconflow.model:Qwen/Qwen3-8B}")
    private String model;

    private static final int MAX_RETRIES = 2;

    private static final String SYSTEM_PROMPT = """
            你是量化交易策略代码生成助手。根据用户描述生成Java代码。
            规范:1)public类PascalCase命名 2)包含public Strategy buildStrategy(BarSeries series)方法 3)必须import以下包:org.ta4j.core.*;org.ta4j.core.indicators.*;org.ta4j.core.indicators.helpers.*;org.ta4j.core.rules.*;
            常用指标:ClosePriceIndicator(series),SMAIndicator(ind,n),EMAIndicator(ind,n),RSIIndicator(ind,n),MACDIndicator(ind,s,l),BollingerBandsUpper/Middle/LowerIndicator,ATRIndicator(series,n),CCIIndicator(series,n),VolumeIndicator(series),StandardDeviationIndicator(ind,n),ConstantIndicator(series,value)
            常用规则:CrossedUpRule(ind1,ind2),CrossedDownRule(ind1,ind2),OverIndicatorRule(ind1,ind2),UnderIndicatorRule(ind1,ind2),IsRisingRule(ind,n),IsFallingRule(ind,n),StopLossRule(series,pct),StopGainRule(series,pct),TrailingStopLossRule(series,pct),MaxTradeBarCountRule(n),BooleanIndicatorRule(ind),InPipeRule(ind,lo,hi)
            注意:OverIndicatorRule和UnderIndicatorRule的第二个参数可以直接用数字,如UnderIndicatorRule(rsi,30)。ClosePriceIndicator在helpers包中,必须import org.ta4j.core.indicators.helpers.*
            不支持的指标用最接近的替代,名称加[部分不支持]
            输出格式:第一行"策略名称:XXX",空一行,然后用```java代码块输出完整代码
            示例:
            策略名称:均线交叉策略

            ```java
            import org.ta4j.core.*;
            import org.ta4j.core.indicators.*;
            import org.ta4j.core.indicators.helpers.*;
            import org.ta4j.core.rules.*;

            public class SmaCrossStrategy {
                public Strategy buildStrategy(BarSeries series) {
                    ClosePriceIndicator close = new ClosePriceIndicator(series);
                    SMAIndicator sma5 = new SMAIndicator(close, 5);
                    SMAIndicator sma20 = new SMAIndicator(close, 20);
                    Rule buyRule = new CrossedUpRule(sma5, sma20);
                    Rule sellRule = new CrossedDownRule(sma5, sma20);
                    return new BaseStrategy(buyRule, sellRule);
                }
            }
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

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", userPrompt));

        if (previousResult != null && previousResult.code != null && previousResult.compileError != null) {
            String assistantContent = "策略名称:" + (previousResult.suggestedName != null ? previousResult.suggestedName : "未命名") + "\n\n```java\n" + previousResult.code + "\n```";
            messages.add(Map.of("role", "assistant", "content", assistantContent));

            String retryContent = "上一次生成的代码编译失败，错误信息:\n" + previousResult.compileError + "\n\n请修复代码并重新生成。确保只使用系统支持的指标和规则。";
            messages.add(Map.of("role", "user", "content", retryContent));
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0.3);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
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
