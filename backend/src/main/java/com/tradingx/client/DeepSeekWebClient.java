package com.tradingx.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Component
public class DeepSeekWebClient {

    private static final String API_URL = "https://chat.deepseek.com/api/v0/chat/completion";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private String userToken;
    private String chatSessionId;
    private int parentMessageId = 0;

    public void setUserToken(String token) {
        if (token.startsWith("{")) {
            try {
                JsonNode node = mapper.readTree(token);
                this.userToken = node.get("value").asText();
            } catch (Exception e) {
                this.userToken = token;
            }
        } else {
            this.userToken = token;
        }
        this.chatSessionId = UUID.randomUUID().toString();
        this.parentMessageId = 0;
    }

    public String chat(String prompt) {
        return chat(prompt, null);
    }

    public String chat(String prompt, Consumer<String> onChunk) {
        if (userToken == null || userToken.isBlank()) {
            throw new IllegalStateException("userToken not set");
        }

        try {
            String requestBody = buildRequestBody(prompt);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + userToken)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36")
                    .header("Accept", "*/*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Origin", "https://chat.deepseek.com")
                    .header("Referer", "https://chat.deepseek.com/")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofMinutes(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.debug("DeepSeek API response status: {}", response.statusCode());
            log.debug("DeepSeek API response body: {}", response.body());
            
            if (response.statusCode() != 200) {
                log.error("DeepSeek API error: {} - {}", response.statusCode(), response.body());
                throw new RuntimeException("DeepSeek API error: " + response.statusCode() + " - " + response.body());
            }

            return parseStreamResponse(response.body(), onChunk);
        } catch (Exception e) {
            log.error("DeepSeek chat failed", e);
            throw new RuntimeException("DeepSeek chat failed: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(String prompt) throws Exception {
        var root = mapper.createObjectNode();
        root.put("chat_session_id", chatSessionId);
        root.put("parent_message_id", parentMessageId);
        root.putNull("model_type");
        root.put("prompt", prompt);
        root.putArray("ref_file_ids");
        root.put("thinking_enabled", true);
        root.put("search_enabled", true);
        root.put("preempt", false);
        return mapper.writeValueAsString(root);
    }

    private String parseStreamResponse(String body, Consumer<String> onChunk) {
        StringBuilder fullContent = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();
        
        try {
            String[] lines = body.split("\n");
            for (String line : lines) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if (data.isEmpty() || data.equals("[DONE]")) {
                        continue;
                    }
                    
                    JsonNode node = mapper.readTree(data);
                    JsonNode choices = node.get("choices");
                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode delta = choices.get(0).get("delta");
                        if (delta != null) {
                            JsonNode contentNode = delta.get("content");
                            JsonNode thinkingNode = delta.get("reasoning_content");
                            
                            if (contentNode != null && !contentNode.isNull()) {
                                String content = contentNode.asText();
                                fullContent.append(content);
                                if (onChunk != null) {
                                    onChunk.accept(content);
                                }
                            }
                            
                            if (thinkingNode != null && !thinkingNode.isNull()) {
                                thinkingContent.append(thinkingNode.asText());
                            }
                        }
                        
                        JsonNode messageId = choices.get(0).get("message_id");
                        if (messageId != null && messageId.isNumber()) {
                            parentMessageId = messageId.asInt();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse stream response", e);
        }
        
        return fullContent.toString();
    }

    public void newSession() {
        this.chatSessionId = UUID.randomUUID().toString();
        this.parentMessageId = 0;
    }
}
