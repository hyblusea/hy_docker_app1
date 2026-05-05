package com.tradingx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AiStrategyService {

    private final StrategyCompiler strategyCompiler;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;

    private static final String SYSTEM_PROMPT = """
            你是量化交易策略代码生成助手。用户消息中包含了从ta4j v0.22.6官方API目录和示例代码中检索到的参考资料。

            严格规则:
            1. 只能使用参考资料中列出的类，如果某个类在参考资料中找不到，说明它不存在于v0.22.6，绝对不能使用
            2. 包名必须是 com.tradingx.strategy
            3. 必须有 public static Strategy buildStrategy(BarSeries series) 方法
            4. 必须返回 new BaseStrategy("策略名", entryRule, exitRule)
            5. Num类在org.ta4j.core.num包，Rule类在org.ta4j.core.rules包，org.ta4j.core.trading包不存在
            6. SMAIndicator/EMAIndicator在org.ta4j.core.indicators.averages包，ADXIndicator在org.ta4j.core.indicators.adx包
            7. KDJ没有单独的Indicator类，用StochasticOscillatorKIndicator和StochasticOscillatorDIndicator代替

            输出格式:第一行"策略名称:XXX",空一行,然后用```java代码块输出完整java类代码```
            只输出以上要求的内容,
            """;

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");

    private static final String PACKAGE_NAME = "com.tradingx.strategy";

    @Value("${spring.ai.openai.chat.model}")
    private String modelName;

    public AiStrategyService(StrategyCompiler strategyCompiler, ChatModel chatModel, VectorStore vectorStore) {
        this.strategyCompiler = strategyCompiler;
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
    }

    private OpenAiChatOptions chatOptions() {
        return OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(0.0)
                .build();
    }

    private String retrieveRagContext(String userQuery) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuery)
                        .topK(5)
                        .similarityThreshold(0.5)
                        .build()
        );
        if (docs == null || docs.isEmpty()) {
            return "";
        }
        return docs.stream()
                .map(doc -> {
                    String source = doc.getMetadata().get("source") != null ? doc.getMetadata().get("source").toString() : "unknown";
                    return "【来源: " + source + "】\n" + doc.getText();
                })
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    public AiGenerateResult generate(String buyDesc, String sellDesc) {
        return generate(buyDesc, sellDesc, null);
    }

    public AiGenerateResult generate(String buyDesc, String sellDesc, Consumer<String> onThinking) {
        String userPrompt = buildUserPrompt(buyDesc, sellDesc);

        try {
            String aiResponse;
            if (onThinking != null) {
                aiResponse = callAiApiStream(userPrompt, null, onThinking);
            } else {
                aiResponse = callAiApi(userPrompt, null);
            }
            AiGenerateResult result = parseAiResponse(aiResponse);

            if (result.code == null || result.code.isBlank()) {
                result = new AiGenerateResult();
                result.valid = false;
                result.compileError = "AI返回结果中未包含有效代码";
                return result;
            }

            String compileError = strategyCompiler.compileCheck(result.code);
            if (compileError == null) {
                result.valid = true;
                log.info("AI生成策略编译通过: {}", result.suggestedName);
                return result;
            }

            result.valid = false;
            result.compileError = compileError;
            log.warn("AI生成策略编译失败: {}", compileError);
            return result;
        } catch (Exception e) {
            log.error("AI策略生成失败: {}", e.getMessage());
            AiGenerateResult result = new AiGenerateResult();
            result.valid = false;
            result.compileError = "AI生成失败: " + e.getMessage();
            return result;
        }
    }

    private String buildUserPrompt(String buyDesc, String sellDesc) {
        return "请根据以下策略描述生成Java代码：\n\n" +
                "买入策略: " + buyDesc + "\n\n" +
                "卖出策略: " + sellDesc;
    }

    private List<Message> buildMessages(String userPrompt, AiGenerateResult previousResult) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));

        String ragContext = retrieveRagContext(userPrompt);
        String augmentedPrompt = userPrompt;
        if (!ragContext.isBlank()) {
            augmentedPrompt = "以下是从ta4j v0.22.6官方文档和示例代码中检索到的参考资料：\n\n" +
                    ragContext +
                    "\n\n---\n\n请基于以上参考资料，" + userPrompt;
        }

        if (previousResult != null && previousResult.code != null && previousResult.compileError != null) {
            messages.add(new UserMessage(augmentedPrompt));
            String assistantContent = "策略名称:" + (previousResult.suggestedName != null ? previousResult.suggestedName : "未命名") + "\n\n```java\n" + previousResult.code + "\n```";
            messages.add(new AssistantMessage(assistantContent));
            messages.add(new UserMessage("上一次生成的代码编译失败，错误信息:\n" + previousResult.compileError + "\n\n请修复代码并重新生成。确保只使用系统支持的指标和规则。"));
        } else {
            messages.add(new UserMessage(augmentedPrompt));
        }

        return messages;
    }

    private String callAiApi(String userPrompt, AiGenerateResult previousResult) {
        Prompt prompt = new Prompt(buildMessages(userPrompt, previousResult), chatOptions());
        return chatModel.call(prompt).getResult().getOutput().getText();
    }

    private String callAiApiStream(String userPrompt, AiGenerateResult previousResult, Consumer<String> onThinking) {
        Prompt prompt = new Prompt(buildMessages(userPrompt, previousResult), chatOptions());
        StringBuilder fullContent = new StringBuilder();
        chatModel.stream(prompt)
                .doOnNext(chatResponse -> {
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        String delta = chatResponse.getResult().getOutput().getText();
                        if (delta != null && !delta.isEmpty()) {
                            fullContent.append(delta);
                            onThinking.accept(delta);
                        }
                    }
                })
                .blockLast();
        return fullContent.toString();
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
