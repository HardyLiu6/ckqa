package org.ysu.ckqaback.integration.graphrag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

import java.time.Duration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * GraphRAG 异步查询任务客户端。
 */
@Service
public class GraphRagTaskClient {

    private final RestClient restClient;
    private final RestClient eventRestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public GraphRagTaskClient(RestClient.Builder builder, CkqaIntegrationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int connectTimeoutMillis = Math.toIntExact(Duration.ofSeconds(
                Math.max(1L, properties.getStreaming().getPythonStreamConnectTimeoutSeconds())
        ).toMillis());
        int readTimeoutMillis = Math.toIntExact(Duration.ofSeconds(
                Math.max(
                        Math.max(1L, properties.getTimeout().getQuerySeconds()),
                        properties.getStreaming().getPythonStreamReadTimeoutSeconds()
                )
        ).toMillis());
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl(properties.getGraphrag().getApiBaseUrl())
                .build();
        SimpleClientHttpRequestFactory eventRequestFactory = new SimpleClientHttpRequestFactory();
        eventRequestFactory.setConnectTimeout(connectTimeoutMillis);
        eventRequestFactory.setReadTimeout(readTimeoutMillis);
        this.eventRestClient = RestClient.builder()
                .requestFactory(eventRequestFactory)
                .baseUrl(properties.getGraphrag().getApiBaseUrl())
                .build();
    }

    GraphRagTaskClient(RestClient.Builder builder, String baseUrl, Duration timeout) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
        this.eventRestClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public GraphRagTaskCreateResult createTask(String mode, String prompt) {
        return createTask(mode, prompt, null, null);
    }

    public GraphRagTaskCreateResult createTask(String mode, String prompt, Long indexRunId, String dataDirUri) {
        return createTask(mode, prompt, indexRunId, dataDirUri, null);
    }

    public GraphRagTaskCreateResult createTask(
            String mode,
            String prompt,
            Long indexRunId,
            String dataDirUri,
            String generationContext
    ) {
        return createTask(mode, prompt, indexRunId, dataDirUri, generationContext, null, null);
    }

    public GraphRagTaskCreateResult createTask(
            String mode,
            String prompt,
            Long indexRunId,
            String dataDirUri,
            String generationContext,
            String queryEngineStrategy,
            List<GraphRagConversationMessage> conversationHistory
    ) {
        return createTask(mode, prompt, indexRunId, dataDirUri, generationContext, queryEngineStrategy, conversationHistory, false);
    }

    public GraphRagTaskCreateResult createTask(
            String mode,
            String prompt,
            Long indexRunId,
            String dataDirUri,
            String generationContext,
            String queryEngineStrategy,
            List<GraphRagConversationMessage> conversationHistory,
            boolean streamResponse
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("prompt", prompt);
        body.put("retrievalQuery", prompt);
        if (streamResponse) {
            body.put("streamResponse", true);
            body.put("streamSource", "native_graphrag");
        }
        if (generationContext != null && !generationContext.isBlank()) {
            body.put("generationContext", generationContext);
        }
        if (indexRunId != null) {
            body.put("indexRunId", indexRunId);
        }
        if (dataDirUri != null && !dataDirUri.isBlank()) {
            body.put("dataDirUri", dataDirUri);
        }
        if (queryEngineStrategy != null && !queryEngineStrategy.isBlank()) {
            body.put("queryEngineStrategy", queryEngineStrategy);
        }
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            body.put("conversationHistory", conversationHistory);
        }
        return restClient.post()
                .uri("/v1/query-tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GraphRagTaskCreateResult.class);
    }

    public Optional<GraphRagTaskSnapshot> getTask(String pythonTaskId) {
        try {
            GraphRagTaskSnapshot snapshot = restClient.get()
                    .uri("/v1/query-tasks/{taskId}", pythonTaskId)
                    .retrieve()
                    .body(GraphRagTaskSnapshot.class);
            return Optional.ofNullable(snapshot);
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    public void streamTaskEvents(String pythonTaskId, Consumer<GraphRagTaskEvent> consumer) {
        streamTaskEvents(pythonTaskId, 0L, consumer);
    }

    public void streamTaskEvents(String pythonTaskId, Long afterEventSeq, Consumer<GraphRagTaskEvent> consumer) {
        long resumeSeq = afterEventSeq == null ? 0L : Math.max(0L, afterEventSeq);
        eventRestClient.get()
                .uri("/v1/query-tasks/{taskId}/events?afterEventSeq={afterEventSeq}", pythonTaskId, resumeSeq)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange((request, response) -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getBody(), StandardCharsets.UTF_8)
                    )) {
                        parseSseEvents(reader, consumer);
                    }
                    return null;
                });
    }

    private void parseSseEvents(BufferedReader reader, Consumer<GraphRagTaskEvent> consumer) throws IOException {
        String eventName = "message";
        Long eventSeq = null;
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                if (!data.isEmpty()) {
                    consumer.accept(new GraphRagTaskEvent(eventName, parseJson(data.toString()), eventSeq));
                }
                eventName = "message";
                eventSeq = null;
                data.setLength(0);
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = line.substring("event:".length()).trim();
            } else if (line.startsWith("id:")) {
                eventSeq = parseEventSeq(line.substring("id:".length()).trim());
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).trim());
            }
        }
        if (!data.isEmpty()) {
            consumer.accept(new GraphRagTaskEvent(eventName, parseJson(data.toString()), eventSeq));
        }
    }

    private Long parseEventSeq(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private JsonNode parseJson(String raw) throws IOException {
        return objectMapper.readTree(raw);
    }

    public GraphRagHybridReadinessResult warmupHybridV0(String dataDirUri) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (dataDirUri != null && !dataDirUri.isBlank()) {
            body.put("dataDirUri", dataDirUri);
        }
        return restClient.post()
                .uri("/v1/hybrid-v0/warmup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(GraphRagHybridReadinessResult.class);
    }

    public GraphRagHybridReadinessResult getHybridV0Readiness(String dataDirUri) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/hybrid-v0/readiness")
                        .queryParam("dataDirUri", dataDirUri)
                        .build())
                .retrieve()
                .body(GraphRagHybridReadinessResult.class);
    }
}
