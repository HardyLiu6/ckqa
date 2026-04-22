package org.ysu.ckqaback.integration.graphrag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * GraphRAG HTTP 问答客户端。
 */
@Service
public class GraphRagQueryClient {

    private final RestClient restClient;

    @Autowired
    public GraphRagQueryClient(RestClient.Builder builder, CkqaIntegrationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.getTimeout().getQuerySeconds());
        requestFactory.setConnectTimeout((int) timeout.toMillis());
        requestFactory.setReadTimeout((int) timeout.toMillis());
        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl(properties.getGraphrag().getApiBaseUrl())
                .build();
    }

    GraphRagQueryClient(RestClient.Builder builder, String baseUrl, Duration timeout) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public GraphRagChatResult query(String mode, String prompt) {
        String model = resolveModel(mode);
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        Map<?, ?> response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        return new GraphRagChatResult(extractContent(response));
    }

    private String resolveModel(String mode) {
        return switch (mode) {
            case "global" -> "graphrag-global-search:latest";
            case "full" -> "full-model:latest";
            default -> "graphrag-local-search:latest";
        };
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<?, ?> response) {
        if (response == null) {
            throw new IllegalStateException("GraphRAG响应为空");
        }
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("GraphRAG响应缺少choices");
        }

        Object firstChoice = choices.getFirst();
        if (!(firstChoice instanceof Map<?, ?> choice)) {
            throw new IllegalStateException("GraphRAG响应choices结构不合法");
        }

        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new IllegalStateException("GraphRAG响应缺少message");
        }

        Object contentValue = message.get("content");
        if (!(contentValue instanceof String content) || content.isBlank()) {
            throw new IllegalStateException("GraphRAG响应缺少content");
        }
        return content;
    }
}
