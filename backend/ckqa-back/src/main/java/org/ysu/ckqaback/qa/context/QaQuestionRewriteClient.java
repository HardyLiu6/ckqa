package org.ysu.ckqaback.qa.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 One API 的 OpenAI-compatible chat completion 端点改写追问。
 */
@Service
public class QaQuestionRewriteClient implements QaQuestionRewriteClientPort {

    private static final String SYSTEM_PROMPT = """
            你是 CKQA 的课程问答追问改写器。
            你的任务是把学生当前追问改写成一个独立、简洁、适合检索课程知识库的问题。
            只能使用给定的会话摘要和最近对话来消解指代，不要补充新知识。
            必须只输出 JSON：{"standaloneQuery":"...","confidence":0.0到1.0,"reason":"..."}。
            如果无法可靠改写，standaloneQuery 保持原问题，confidence 低于 0.6。
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int maxChars;
    private final boolean thinkingDisabled;

    @Autowired
    public QaQuestionRewriteClient(
            RestClient.Builder builder,
            CkqaIntegrationProperties properties
    ) {
        this(
                withTimeout(builder, Duration.ofSeconds(properties.getRewrite().getTimeoutSeconds())),
                new ObjectMapper(),
                properties.getRewrite().getApiBaseUrl(),
                properties.getRewrite().getApiKey(),
                properties.getRewrite().getModel(),
                properties.getRewrite().getMaxChars(),
                properties.getRewrite().isThinkingDisabled()
        );
    }

    QaQuestionRewriteClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey,
            String model,
            int maxChars,
            boolean thinkingDisabled
    ) {
        this.restClient = builder.baseUrl(trimTrailingSlash(baseUrl)).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = StringUtils.hasText(model) ? model : "deepseek-v4-flash";
        this.maxChars = maxChars > 0 ? maxChars : 800;
        this.thinkingDisabled = thinkingDisabled;
    }

    @Override
    public QaLlmQuestionRewriteResult rewrite(String originalQuestion, QaContextAssembly context) {
        try {
            ChatCompletionResponse response = request()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(originalQuestion, context))
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            String content = extractContent(response);
            if (!StringUtils.hasText(content)) {
                return QaLlmQuestionRewriteResult.failure("模型返回空改写结果", model);
            }
            return parseContent(content.trim());
        } catch (RestClientException | IllegalArgumentException exception) {
            String message = exception.getMessage() == null ? "追问改写模型调用失败" : exception.getMessage();
            return QaLlmQuestionRewriteResult.failure(message, model);
        }
    }

    private RestClient.RequestBodySpec request() {
        RestClient.RequestBodySpec spec = restClient.post().uri("/chat/completions");
        if (StringUtils.hasText(apiKey)) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }
        return spec;
    }

    private Map<String, Object> buildRequestBody(String originalQuestion, QaContextAssembly context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("temperature", 0.0);
        body.put("max_tokens", Math.max(256, maxChars));
        body.put("response_format", Map.of("type", "json_object"));
        if (thinkingDisabled) {
            body.put("thinking", Map.of("type", "disabled"));
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", buildUserPrompt(originalQuestion, context))
        ));
        return body;
    }

    private String buildUserPrompt(String originalQuestion, QaContextAssembly context) {
        StringBuilder builder = new StringBuilder();
        builder.append("最近可用主题：")
                .append(StringUtils.hasText(context.latestTopic()) ? context.latestTopic() : "无")
                .append("\n\n上下文快照：\n")
                .append(StringUtils.hasText(context.snapshotText()) ? context.snapshotText() : "无")
                .append("\n\n当前学生问题：\n")
                .append(originalQuestion == null ? "" : originalQuestion.trim())
                .append("\n\n请只输出 JSON。");
        return builder.toString();
    }

    private QaLlmQuestionRewriteResult parseContent(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            String standaloneQuery = firstText(root, "standaloneQuery", "standalone_query", "query");
            double confidence = firstDouble(root, "confidence", "score");
            String reason = firstText(root, "reason", "rewriteReason", "rewrite_reason");
            if (!StringUtils.hasText(standaloneQuery)) {
                return QaLlmQuestionRewriteResult.failure("模型 JSON 缺少 standaloneQuery", model);
            }
            return QaLlmQuestionRewriteResult.success(standaloneQuery.trim(), confidence, reason, model);
        } catch (Exception exception) {
            return QaLlmQuestionRewriteResult.failure("模型返回非严格 JSON", model);
        }
    }

    private String firstText(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return "";
    }

    private double firstDouble(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null && value.isNumber()) {
                return value.asDouble();
            }
        }
        return 0D;
    }

    private String extractContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return "";
        }
        ChatCompletionChoice choice = response.choices().get(0);
        if (choice == null || choice.message() == null) {
            return "";
        }
        return choice.message().content();
    }

    private String trimTrailingSlash(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "http://127.0.0.1:3000/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static RestClient.Builder withTimeout(RestClient.Builder builder, Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(timeout.toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return builder.clone().requestFactory(requestFactory);
    }

    private record ChatCompletionResponse(List<ChatCompletionChoice> choices) {
    }

    private record ChatCompletionChoice(ChatCompletionMessage message) {
    }

    private record ChatCompletionMessage(String content) {
    }
}
