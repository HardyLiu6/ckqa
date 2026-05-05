package org.ysu.ckqaback.integration.graphrag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * GraphRAG 异步查询任务客户端。
 */
@Service
public class GraphRagTaskClient {

    private final RestClient restClient;

    @Autowired
    public GraphRagTaskClient(RestClient.Builder builder, CkqaIntegrationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.getPolling().getQueryTaskIntervalSeconds());
        int timeoutMillis = Math.toIntExact(timeout.toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl(properties.getGraphrag().getApiBaseUrl())
                .build();
    }

    GraphRagTaskClient(RestClient.Builder builder, String baseUrl, Duration timeout) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public GraphRagTaskCreateResult createTask(String mode, String prompt) {
        return createTask(mode, prompt, null, null);
    }

    public GraphRagTaskCreateResult createTask(String mode, String prompt, Long indexRunId, String dataDirUri) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mode", mode);
        body.put("prompt", prompt);
        if (indexRunId != null) {
            body.put("indexRunId", indexRunId);
        }
        if (dataDirUri != null && !dataDirUri.isBlank()) {
            body.put("dataDirUri", dataDirUri);
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
}
