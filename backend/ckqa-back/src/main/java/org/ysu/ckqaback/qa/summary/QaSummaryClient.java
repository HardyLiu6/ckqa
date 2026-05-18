package org.ysu.ckqaback.qa.summary;

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
 * 调用 One API 的 OpenAI-compatible chat completion 端点生成会话滚动摘要。
 */
@Service
public class QaSummaryClient implements QaSummaryClientPort {

    private static final String SYSTEM_PROMPT = """
            你是 CKQA 的课程问答会话摘要器。
            只总结已有对话，不补充新知识，不推测学生没有问过的内容。
            用中文输出 800 字以内摘要，覆盖：学生关注的问题、已解释的关键概念、已给出的结论、未解决或可继续追问。
            """;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final int maxChars;
    private final boolean thinkingDisabled;

    @Autowired
    public QaSummaryClient(RestClient.Builder builder, CkqaIntegrationProperties properties) {
        this(
                withTimeout(builder, Duration.ofSeconds(properties.getSummary().getTimeoutSeconds())),
                properties.getSummary().getApiBaseUrl(),
                properties.getSummary().getApiKey(),
                properties.getSummary().getModel(),
                properties.getSummary().getMaxChars(),
                Duration.ofSeconds(properties.getSummary().getTimeoutSeconds()),
                properties.getSummary().isThinkingDisabled()
        );
    }

    QaSummaryClient(
            RestClient.Builder builder,
            String baseUrl,
            String apiKey,
            String model,
            int maxChars,
            Duration timeout,
            boolean thinkingDisabled
    ) {
        this.restClient = builder
                .baseUrl(trimTrailingSlash(baseUrl))
                .build();
        this.apiKey = apiKey;
        this.model = StringUtils.hasText(model) ? model : "deepseek-v4-flash";
        this.maxChars = maxChars > 0 ? maxChars : 800;
        this.thinkingDisabled = thinkingDisabled;
    }

    @Override
    public QaSummaryResult summarize(String previousSummary, String conversationText) {
        long startedNanos = System.nanoTime();
        int inputCharCount = lengthOf(previousSummary) + lengthOf(conversationText);
        try {
            ChatCompletionResponse response = request()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequestBody(previousSummary, conversationText))
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            String content = extractContent(response);
            if (!StringUtils.hasText(content)) {
                return QaSummaryResult.failure("模型返回空摘要", model, elapsedMs(startedNanos), inputCharCount);
            }
            String summary = truncate(content.trim(), maxChars);
            return QaSummaryResult.success(summary, model, elapsedMs(startedNanos), inputCharCount, summary.length());
        } catch (RestClientException | IllegalArgumentException exception) {
            return QaSummaryResult.failure(
                    exception.getMessage() == null ? "摘要模型调用失败" : exception.getMessage(),
                    model,
                    elapsedMs(startedNanos),
                    inputCharCount
            );
        }
    }

    private RestClient.RequestBodySpec request() {
        RestClient.RequestBodySpec spec = restClient.post().uri("/chat/completions");
        if (StringUtils.hasText(apiKey)) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }
        return spec;
    }

    private Map<String, Object> buildRequestBody(String previousSummary, String conversationText) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("temperature", 0.2);
        body.put("max_tokens", Math.max(512, maxChars * 2));
        if (thinkingDisabled) {
            body.put("thinking", Map.of("type", "disabled"));
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", buildUserPrompt(previousSummary, conversationText))
        ));
        return body;
    }

    private String buildUserPrompt(String previousSummary, String conversationText) {
        StringBuilder builder = new StringBuilder();
        builder.append("上一版摘要：\n");
        builder.append(StringUtils.hasText(previousSummary) ? previousSummary.trim() : "无");
        builder.append("\n\n本轮新增的连续已完成对话：\n");
        builder.append(StringUtils.hasText(conversationText) ? conversationText.trim() : "无");
        builder.append("\n\n请输出更新后的滚动摘要。");
        return builder.toString();
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String trimTrailingSlash(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "http://127.0.0.1:3000/v1";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
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
