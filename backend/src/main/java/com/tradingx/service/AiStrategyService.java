package com.tradingx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AiStrategyService {

    private final StrategyCompiler strategyCompiler;
    private final ChatClient chatClient;
    private final OpenAiChatModel chatModel;

    private static final int MAX_RETRIES = 2;

    private static final String SYSTEM_PROMPT = """
            你是量化交易策略代码生成助手。根据用户描述生成java ta4j 0.22.6 库的策略类，包名 com.tradingx.strategy,
            输出格式:第一行"策略名称:XXX",空一行,然后用```java代码块输出完整java类代码
            只输出以上要求的内容
            """;

    private static final List<String> REQUIRED_IMPORTS = List.of(
            "import org.ta4j.core.*;",
            "import org.ta4j.core.indicators.*;",
            "import org.ta4j.core.indicators.averages.*;",
            "import org.ta4j.core.indicators.helpers.*;",
            "import org.ta4j.core.rules.*;"
    );

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("public\\s+class\\s+(\\w+)");

    public AiStrategyService(StrategyCompiler strategyCompiler, ChatClient.Builder chatClientBuilder, OpenAiChatModel chatModel) {
        this.strategyCompiler = strategyCompiler;
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.chatModel = chatModel;
    }

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

    private String callAiApi(String userPrompt, AiGenerateResult previousResult) {
        if (previousResult != null && previousResult.code != null && previousResult.compileError != null) {
            String assistantContent = "策略名称:" + (previousResult.suggestedName != null ? previousResult.suggestedName : "未命名") + "\n\n```java\n" + previousResult.code + "\n```";
            String retryContent = "上一次生成的代码编译失败，错误信息:\n" + previousResult.compileError + "\n\n请修复代码并重新生成。确保只使用系统支持的指标和规则。";

            List<Message> messages = new ArrayList<>();
            messages.add(new UserMessage(userPrompt));
            messages.add(new AssistantMessage(assistantContent));
            messages.add(new UserMessage(retryContent));

            Prompt prompt = new Prompt(messages);
            return chatModel.call(prompt).getResult().getOutput().getText();
        }

        return chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
    }

    private String callAiApiStream(String userPrompt, AiGenerateResult previousResult, Consumer<String> onThinking) {
        if (previousResult != null && previousResult.code != null && previousResult.compileError != null) {
            String assistantContent = "策略名称:" + (previousResult.suggestedName != null ? previousResult.suggestedName : "未命名") + "\n\n```java\n" + previousResult.code + "\n```";
            String retryContent = "上一次生成的代码编译失败，错误信息:\n" + previousResult.compileError + "\n\n请修复代码并重新生成。确保只使用系统支持的指标和规则。";

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SYSTEM_PROMPT));
            messages.add(new UserMessage(userPrompt));
            messages.add(new AssistantMessage(assistantContent));
            messages.add(new UserMessage(retryContent));

            Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder().build());
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

        StringBuilder fullContent = new StringBuilder();
        chatClient.prompt()
                .user(userPrompt)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    fullContent.append(chunk);
                    onThinking.accept(chunk);
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

        code = ensureImports(code);

        result.suggestedName = nameLine;
        result.code = code;
        return result;
    }

    private static final String PACKAGE_NAME = "com.tradingx.strategy";

    private String ensureImports(String code) {
        if (!code.contains("package " + PACKAGE_NAME)) {
            code = "package " + PACKAGE_NAME + ";\n\n" + code;
        }

        for (String imp : REQUIRED_IMPORTS) {
            if (!code.contains(imp)) {
                int classIdx = code.indexOf("public class");
                if (classIdx > 0) {
                    code = code.substring(0, classIdx) + imp + "\n" + code.substring(classIdx);
                } else {
                    code = imp + "\n" + code;
                }
            }
        }

        code = code.replace("new CrossedUpRule(", "new CrossedUpIndicatorRule(");
        code = code.replace("new CrossedDownRule(", "new CrossedDownIndicatorRule(");

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
