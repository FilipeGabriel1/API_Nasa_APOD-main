package com.nasa.apod.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class OpenAiCompatibleChatService implements GenerativeAiChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiCompatibleChatService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final String chatCompletionsUrl;
    private final String apiKey;
    private final String authHeaderName;
    private final String authScheme;
    private final String model;
    private final boolean includeModel;
    private final String systemPrompt;
    private final Duration timeout;

    private final KnowledgeBaseService knowledgeBaseService;
    private final boolean knowledgeStrict;
    private final int knowledgeTopK;
    private final int knowledgeMaxChars;

    public OpenAiCompatibleChatService(
            ObjectMapper objectMapper,
            @Value("${ai.chat.url:${AI_CHAT_URL:}}") String chatCompletionsUrl,
            @Value("${ai.chat.apiKey:${AI_API_KEY:}}") String apiKey,
            @Value("${ai.chat.authHeader:${AI_CHAT_AUTH_HEADER:Authorization}}") String authHeaderName,
            @Value("${ai.chat.authScheme:${AI_CHAT_AUTH_SCHEME:Bearer}}") String authScheme,
            @Value("${ai.chat.model:${AI_CHAT_MODEL:gpt-4o-mini}}") String model,
            @Value("${ai.chat.includeModel:${AI_CHAT_INCLUDE_MODEL:true}}") boolean includeModel,
            @Value("${ai.chat.systemPrompt:${AI_CHAT_SYSTEM_PROMPT:Você é um assistente útil. Responda em português, de forma clara e curta. Se não souber, diga que não sabe.}}") String systemPrompt,
            @Value("${ai.chat.timeoutSeconds:${AI_CHAT_TIMEOUT_SECONDS:30}}") int timeoutSeconds,
            KnowledgeBaseService knowledgeBaseService,
            @Value("${ai.chat.knowledgeStrict:${AI_CHAT_KNOWLEDGE_STRICT:false}}") boolean knowledgeStrict,
            @Value("${ai.chat.knowledgeTopK:${AI_CHAT_KNOWLEDGE_TOPK:4}}") int knowledgeTopK,
            @Value("${ai.chat.knowledgeMaxChars:${AI_CHAT_KNOWLEDGE_MAXCHARS:3500}}") int knowledgeMaxChars) {

        this.objectMapper = objectMapper;
        this.chatCompletionsUrl = chatCompletionsUrl;
        this.apiKey = apiKey;
        this.authHeaderName = authHeaderName;
        this.authScheme = authScheme;
        this.model = model;
        this.includeModel = includeModel;
        this.systemPrompt = systemPrompt;
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();

        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeStrict = knowledgeStrict;
        this.knowledgeTopK = Math.max(1, Math.min(knowledgeTopK, 8));
        this.knowledgeMaxChars = Math.max(500, Math.min(knowledgeMaxChars, 10_000));
    }

    @Override
    public String generateReply(String userMessage, String context) {
        if (chatCompletionsUrl == null || chatCompletionsUrl.isBlank()) {
            throw new IllegalStateException("IA não configurada: defina AI_CHAT_URL (ou 'ai.chat.url').");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("IA não configurada: defina AI_API_KEY (ou 'ai.chat.apiKey').");
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            if (includeModel) {
                requestBody.put("model", model);
            }
            requestBody.put("temperature", 0.4);

            ArrayNode messages = requestBody.putArray("messages");

            ObjectNode system = objectMapper.createObjectNode();
            system.put("role", "system");
            system.put("content", buildSystemPrompt());
            messages.add(system);

            String kb = knowledgeBaseService != null
                    ? knowledgeBaseService.retrieveRelevantContext(userMessage, knowledgeTopK, knowledgeMaxChars)
                    : "";
            if (kb != null && !kb.isBlank()) {
                ObjectNode kbNode = objectMapper.createObjectNode();
                kbNode.put("role", "system");
                kbNode.put("content", "Material de referência (use para responder):\n" + kb);
                messages.add(kbNode);
            }

            if (context != null && !context.isBlank()) {
                ObjectNode ctx = objectMapper.createObjectNode();
                ctx.put("role", "system");
                ctx.put("content", "Contexto adicional (pode usar para responder):\n" + context.trim());
                messages.add(ctx);
            }

            ObjectNode user = objectMapper.createObjectNode();
            user.put("role", "user");
            user.put("content", userMessage);
            messages.add(user);

            String payload = objectMapper.writeValueAsString(requestBody);
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsUrl))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload));

            if (authHeaderName != null && !authHeaderName.isBlank()) {
                String value = (authScheme == null || authScheme.isBlank())
                        ? apiKey
                        : authScheme.trim() + " " + apiKey;
                reqBuilder.header(authHeaderName, value);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body();

            if (status < 200 || status >= 300) {
                LOGGER.warn("IA provider retornou status={} body={}", status, shrink(body));
                throw new RuntimeException("IA provider retornou status " + status);
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                content = root.path("choices").path(0).path("text");
            }

            String reply = content.isTextual() ? content.asText() : null;
            if (reply == null || reply.isBlank()) {
                LOGGER.warn("Resposta vazia da IA. Body={}", shrink(body));
                return "Não consegui gerar uma resposta agora. Tente reformular a pergunta.";
            }
            return reply.trim();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Falha ao chamar IA", e);
        }
    }

    private String buildSystemPrompt() {
        if (!knowledgeStrict) {
            return systemPrompt;
        }

        return systemPrompt + "\n\nRegra de segurança: se a resposta não estiver no Material de referência, diga que não sabe e peça mais detalhes.";
    }

    private static String shrink(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= 600) {
            return trimmed;
        }
        return trimmed.substring(0, 600) + "...";
    }
}
