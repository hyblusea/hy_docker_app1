package com.tradingx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiStrategyService {

    private final StrategyCompiler strategyCompiler;
    private final ChatModel chatModel;

    private static final String SYSTEM_PROMPT = """
            你是量化交易策略代码生成助手，你需要仔细了解ta4j v0.22.6库的使用,仔细阅读代码仓库https://github.com/ta4j/ta4j/tree/0.22.6 中的代码
            根据用户描述生成java ta4j v0.22.6 库的策略类，包名 com.tradingx.strategy,
            不要使用了ta4j 其他版本的库，只能import v0.22.6版本的ta4j库
            输出格式:第一行"策略名称:XXX",空一行,然后用```java代码块输出完整java类代码```
            只输出以上要求的内容,
            """;

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");

    private static final String PACKAGE_NAME = "com.tradingx.strategy";

    @Value("${spring.ai.openai.chat.model}")
    private String modelName;

    public AiStrategyService(StrategyCompiler strategyCompiler, ChatModel chatModel) {
        this.strategyCompiler = strategyCompiler;
        this.chatModel = chatModel;
    }

    private OpenAiChatOptions chatOptions() {
        return OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(0.0)
                .build();
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

        if (previousResult != null && previousResult.code != null && previousResult.compileError != null) {
            messages.add(new UserMessage(userPrompt));
            String assistantContent = "策略名称:" + (previousResult.suggestedName != null ? previousResult.suggestedName : "未命名") + "\n\n```java\n" + previousResult.code + "\n```";
            messages.add(new AssistantMessage(assistantContent));
            messages.add(new UserMessage("上一次生成的代码编译失败，错误信息:\n" + previousResult.compileError + "\n\n请修复代码并重新生成。确保只使用系统支持的指标和规则。"));
        } else {
            messages.add(new UserMessage(userPrompt));
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
                    String delta = chatResponse.getResult() != null
                            ? chatResponse.getResult().getOutput().getText()
                            : null;
                    if (delta != null && !delta.isEmpty()) {
                        fullContent.append(delta);
                        onThinking.accept(delta);
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
