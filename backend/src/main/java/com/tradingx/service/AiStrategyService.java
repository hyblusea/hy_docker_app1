package com.tradingx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

    @Value("${ai.siliconflow.base-url:https://open.bigmodel.cn/api/paas/v4}")
    private String baseUrl;

    @Value("${ai.siliconflow.model:GLM-4.7-FlashX}")
    private String model;

    private static final int MAX_RETRIES = 2;

    private static final String SYSTEM_PROMPT = """
            你是量化交易策略代码生成助手。根据用户描述生成Java代码。
            规范:1)public类PascalCase命名 2)包含public Strategy buildStrategy(BarSeries series)方法 3)只允许使用以下5个import语句,不要添加其他import:
            import org.ta4j.core.*;
            import org.ta4j.core.indicators.*;
            import org.ta4j.core.indicators.averages.*;
            import org.ta4j.core.indicators.helpers.*;
            import org.ta4j.core.rules.*;
            注意:这5个import已覆盖所有需要的类,不需要再import任何其他包
            常用指标:ClosePriceIndicator(series),SMAIndicator(ind,n),EMAIndicator(ind,n),RSIIndicator(ind,n),MACDIndicator(close,shortN,longN)信号线=EMAIndicator(macd,signalN),ATRIndicator(series,n),CCIIndicator(series,n),VolumeIndicator(series),StandardDeviationIndicator(ind,n),ConstantIndicator(series,value),BollingerBandsUpper/Middle/LowerIndicator
            常用规则:CrossedUpIndicatorRule(ind1,ind2),CrossedDownIndicatorRule(ind1,ind2),OverIndicatorRule(ind1,ind2),UnderIndicatorRule(ind1,ind2),IsRisingRule(ind,n),IsFallingRule(ind,n),StopLossRule(series,pct),StopGainRule(series,pct),TrailingStopLossRule(series,pct),MaxTradeBarCountRule(n),BooleanIndicatorRule(ind),InPipeRule(ind,lo,hi)
            注意:1)规则名是CrossedUpIndicatorRule不是CrossedUpRule 2)OverIndicatorRule和UnderIndicatorRule的第二个参数可以直接用数字 3)MACDIndicator(close,12,26)返回MACD线,信号线=EMAIndicator(macd,9) 4)不要使用getMacdLine()或getSignalLine()方法
            不支持的指标用最接近的替代,名称加[部分不支持]
            输出格式:第一行"策略名称:XXX",空一行,然后用```java代码块输出完整代码
            示例:
            策略名称:MACD交叉策略

            ```java
            import org.ta4j.core.*;
            import org.ta4j.core.indicators.*;
            import org.ta4j.core.indicators.averages.*;
            import org.ta4j.core.indicators.helpers.*;
            import org.ta4j.core.rules.*;

            public class MacdCrossStrategy {
                public Strategy buildStrategy(BarSeries series) {
                    ClosePriceIndicator close = new ClosePriceIndicator(series);
                    MACDIndicator macd = new MACDIndicator(close, 12, 26);
                    EMAIndicator signal = new EMAIndicator(macd, 9);
                    Rule buyRule = new CrossedUpIndicatorRule(macd, signal);
                    Rule sellRule = new CrossedDownIndicatorRule(macd, signal);
                    return new BaseStrategy(buyRule, sellRule);
                }
            }
            ```
            """;

    public AiGenerateResult generate(String buyDesc, String sellDesc) {
        return generate(buyDesc, sellDesc, null);
    }

    public AiGenerateResult generate(String buyDesc, String sellDesc, Consumer<String> onThinking) {
        String userPrompt = buildUserPrompt(buyDesc, sellDesc);
        AiGenerateResult result = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                String aiResponse;
                if (onThinking != null) {
                    aiResponse = callAiApiStream(userPrompt, attempt > 0 ? result : null, onThinking);
                } else {
                    aiResponse = callAiApi(userPrompt, attempt > 0 ? result : null);
                }
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

    private String callAiApiStream(String userPrompt, AiGenerateResult previousResult, Consumer<String> onThinking) throws Exception {
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
        requestBody.put("stream", true);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpURLConnection conn = (HttpURLConnection) URI.create(baseUrl + "/chat/completions").toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Accept", "text/event-stream");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(300000);

        conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));

        StringBuilder fullContent = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    try {
                        JsonNode chunk = objectMapper.readTree(data);
                        String delta = chunk.path("choices").path(0).path("delta").path("content").asText("");
                        if (!delta.isEmpty()) {
                            fullContent.append(delta);
                            onThinking.accept(delta);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } finally {
            conn.disconnect();
        }

        return fullContent.toString();
    }

    private static final List<String> REQUIRED_IMPORTS = List.of(
            "import org.ta4j.core.*;",
            "import org.ta4j.core.indicators.*;",
            "import org.ta4j.core.indicators.averages.*;",
            "import org.ta4j.core.indicators.helpers.*;",
            "import org.ta4j.core.rules.*;"
    );

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

        code = ensureImports(code);

        result.suggestedName = nameLine;
        result.code = code;
        return result;
    }

    private String ensureImports(String code) {
        String[] lines = code.split("\n");
        List<String> codeLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import ")) {
                boolean isRequired = false;
                for (String imp : REQUIRED_IMPORTS) {
                    if (trimmed.equals(imp)) {
                        isRequired = true;
                        break;
                    }
                }
                if (!isRequired) continue;
            }
            codeLines.add(line);
        }

        String result = String.join("\n", codeLines);
        for (String imp : REQUIRED_IMPORTS) {
            if (!result.contains(imp)) {
                int classIdx = result.indexOf("public class");
                if (classIdx > 0) {
                    result = imp + "\n" + result.substring(0, classIdx) + result.substring(classIdx);
                } else {
                    result = imp + "\n" + result;
                }
            }
        }

        result = result.replace("new CrossedUpRule(", "new CrossedUpIndicatorRule(");
        result = result.replace("new CrossedDownRule(", "new CrossedDownIndicatorRule(");

        return result;
    }

    public static class AiGenerateResult {
        public String suggestedName;
        public String code;
        public boolean valid;
        public String compileError;
    }
}
