package com.tradingx.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class MimoWebClient {

    private static final Logger log = LoggerFactory.getLogger(MimoWebClient.class);

    private static final String API_URL = "https://aistudio.xiaomimimo.com/open-apis/bot/chat";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String LOG_FILE = "mimo-debug.log";

    private String userId;
    private String xiaomichatbotPh;
    private String serviceToken;
    private String conversationId;
    private String lastMsgId;

    public void setCredentials(String userId, String xiaomichatbotPh, String serviceToken) {
        this.userId = userId;
        this.xiaomichatbotPh = xiaomichatbotPh;
        this.serviceToken = serviceToken;
        this.conversationId = generateUUID();
        this.lastMsgId = null;
        log.info("MiMo credentials set: userId={}", userId);
    }

    public String chat(String prompt) {
        return chat(prompt, null);
    }

    public String chat(String prompt, Consumer<String> onChunk) {
        if (userId == null || userId.isBlank() ||
            xiaomichatbotPh == null || xiaomichatbotPh.isBlank() ||
            serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("MiMo credentials not set");
        }

        HttpURLConnection conn = null;
        try (PrintWriter logWriter = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            String requestBody = buildRequestBody(prompt);
            String urlStr = API_URL + "?xiaomichatbot_ph=" + URLEncoder.encode(xiaomichatbotPh, "UTF-8");

            logWriter.println("\n========== " + LocalDateTime.now() + " ==========");
            logWriter.println("REQUEST: " + requestBody);
            logWriter.flush();

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(300000);

            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "text/event-stream, text/plain, */*");
            conn.setRequestProperty("Cookie", buildCookie());
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/120.0.0.0");
            conn.setRequestProperty("Referer", "https://aistudio.xiaomimimo.com/");
            conn.setRequestProperty("Origin", "https://aistudio.xiaomimimo.com");

            log.info("MiMo API request sent, model=mimo-v2.5-pro");

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = conn.getResponseCode();
            log.info("MiMo API response status: {}", statusCode);

            if (statusCode != 200) {
                String errorBody;
                try (BufferedReader er = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = er.readLine()) != null) {
                        sb.append(line);
                    }
                    errorBody = sb.toString();
                }
                log.error("MiMo API error: {} - {}", statusCode, errorBody);
                throw new RuntimeException("MiMo API error: " + statusCode + " - " + errorBody);
            }

            return readStreamResponse(conn, onChunk, logWriter);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("MiMo chat failed", e);
            throw new RuntimeException("MiMo chat failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readStreamResponse(HttpURLConnection conn, Consumer<String> onChunk, PrintWriter logWriter) throws Exception {
        StringBuilder rawAll = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int dataLineCount = 0;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("id:")) {
                    lastMsgId = line.substring(3).trim();
                    continue;
                }

                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || data.equals("[DONE]")) {
                        logWriter.println("[DONE]");
                        logWriter.flush();
                        continue;
                    }

                    dataLineCount++;

                    try {
                        JsonNode node = mapper.readTree(data);

                        if (node.has("conversationId")) {
                            conversationId = node.get("conversationId").asText();
                        }
                        if (node.has("id")) {
                            lastMsgId = node.get("id").asText();
                        }

                        String rawContent = extractContentField(node);
                        if (rawContent == null || rawContent.isEmpty()) {
                            continue;
                        }

                        rawAll.append(rawContent);

                        String cleaned = rawContent.replace("\u0000", "");
                        if (!cleaned.isEmpty() && onChunk != null) {
                            onChunk.accept(cleaned);
                        }
                    } catch (Exception e) {
                        logWriter.println("PARSE ERROR #" + dataLineCount + ": " + e.getMessage());
                        logWriter.flush();
                    }
                }
            }

            logWriter.println("\nTotal data lines: " + dataLineCount);
            logWriter.println("Raw content length: " + rawAll.length());
            logWriter.flush();

            String fullText = rawAll.toString().replace("\u0000", "");

            String content = extractContentAfterThink(fullText);

            logWriter.println("\n--- FULL TEXT (first 2000 chars) ---");
            logWriter.println(fullText.substring(0, Math.min(fullText.length(), 2000)));
            logWriter.println("\n--- EXTRACTED CONTENT (length=" + content.length() + ") ---");
            logWriter.println(content.substring(0, Math.min(content.length(), 3000)));
            logWriter.flush();

            log.info("Stream finished. Data lines: {}, Raw length: {}, Content length: {}",
                    dataLineCount, fullText.length(), content.length());

            return content;
        }
    }

    private String extractContentAfterThink(String fullText) {
        int thinkEndIdx = fullText.indexOf("</think");
        if (thinkEndIdx < 0) {
            return fullText;
        }
        String content = fullText.substring(thinkEndIdx + "</think".length());
        while (content.startsWith(">") || content.startsWith("\n") || content.startsWith("\r") || content.startsWith(" ")) {
            content = content.substring(1);
        }
        if (content.isEmpty()) {
            log.warn("No content after </think, returning full text");
            return fullText;
        }
        return content;
    }

    private String extractContentField(JsonNode node) {
        if (node.has("content") && !node.get("content").isNull()) {
            JsonNode contentNode = node.get("content");
            if (contentNode.isTextual()) {
                return contentNode.asText();
            }
            if (contentNode.isObject() && contentNode.has("text")) {
                return contentNode.get("text").asText();
            }
        }
        if (node.has("text") && !node.get("text").isNull()) {
            return node.get("text").asText();
        }
        return null;
    }

    private String buildRequestBody(String prompt) throws Exception {
        var root = mapper.createObjectNode();
        root.put("msgId", generateUUID());
        root.put("conversationId", conversationId);
        root.put("query", prompt);
        root.putArray("messages");
        if (lastMsgId != null) {
            root.put("parentId", lastMsgId);
        } else {
            root.putNull("parentId");
        }
        root.put("save", true);
        root.put("source", "STATION");
        root.put("scene", "STATION");

        ObjectNode thinkingNode = mapper.createObjectNode();
        thinkingNode.put("type", "disabled");
        root.set("thinking", thinkingNode);

        var modelConfig = root.putObject("modelConfig");
        modelConfig.put("enableThinking", false);
        modelConfig.put("model", "mimo-v2.5-pro");
        modelConfig.put("temperature", 0.7);
        modelConfig.put("topP", 0.95);

        root.putArray("multiMedias");

        return mapper.writeValueAsString(root);
    }

    private String buildCookie() {
        return String.format("userId=%s; xiaomichatbot_ph=%s; serviceToken=%s",
                userId, xiaomichatbotPh, serviceToken);
    }

    private String generateUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public void newSession() {
        this.conversationId = generateUUID();
        this.lastMsgId = null;
    }
}
