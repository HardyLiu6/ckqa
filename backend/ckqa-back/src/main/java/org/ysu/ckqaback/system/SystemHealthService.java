package org.ysu.ckqaback.system;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.system.dto.SystemHealthItemResponse;
import org.ysu.ckqaback.system.dto.SystemHealthResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 系统健康检查服务。
 */
@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final JdbcTemplate jdbcTemplate;
    private final CkqaIntegrationProperties properties;
    private final RestClient.Builder restClientBuilder;

    public SystemHealthResponse check() {
        List<SystemHealthItemResponse> items = List.of(
                checkMySql(),
                checkPath("pdf-ingest-root", properties.getPdfIngest().getRoot()),
                checkPath("graphrag-root", properties.getGraphrag().getRoot()),
                checkGraphRagOutput(),
                checkApiReachable(),
                checkApiReady()
        );
        boolean up = items.stream().allMatch(SystemHealthItemResponse::isReachable);
        return new SystemHealthResponse(up, items);
    }

    private SystemHealthItemResponse checkMySql() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return SystemHealthItemResponse.of("mysql", true, Integer.valueOf(1).equals(value), "SELECT 1");
        } catch (Exception exception) {
            return SystemHealthItemResponse.of("mysql", false, false, exception.getMessage());
        }
    }

    private SystemHealthItemResponse checkPath(String name, String rawPath) {
        try {
            if (!StringUtils.hasText(rawPath)) {
                return SystemHealthItemResponse.of(name, false, false, "path not configured");
            }
            Path path = Path.of(rawPath);
            boolean exists = Files.exists(path);
            return SystemHealthItemResponse.of(name, exists, exists, exists ? "path exists" : "path missing");
        } catch (Exception exception) {
            return SystemHealthItemResponse.of(name, false, false, exception.getMessage());
        }
    }

    private SystemHealthItemResponse checkApiReachable() {
        String baseUrl = properties.getGraphrag().getApiBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return SystemHealthItemResponse.of("graphrag-api", false, false, "api base url not configured");
        }
        try {
            restClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/health")
                    .retrieve()
                    .toBodilessEntity();
            return SystemHealthItemResponse.of("graphrag-api", true, true, "HTTP /health reachable");
        } catch (Exception exception) {
            return SystemHealthItemResponse.of("graphrag-api", false, false, exception.getMessage());
        }
    }

    private SystemHealthItemResponse checkApiReady() {
        String baseUrl = properties.getGraphrag().getApiBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return SystemHealthItemResponse.of("graphrag-ready", false, false, "api base url not configured");
        }
        try {
            restClientBuilder.build()
                    .get()
                    .uri(baseUrl + "/v1/models")
                    .retrieve()
                    .toEntity(String.class);
            return SystemHealthItemResponse.of("graphrag-ready", true, true, "HTTP /v1/models reachable");
        } catch (Exception exception) {
            return SystemHealthItemResponse.of("graphrag-ready", false, false, exception.getMessage());
        }
    }

    private SystemHealthItemResponse checkGraphRagOutput() {
        String rawRoot = properties.getGraphrag().getRoot();
        if (!StringUtils.hasText(rawRoot)) {
            return SystemHealthItemResponse.of("graphrag-output", false, false, "graphrag root not configured");
        }

        try {
            Path root = Path.of(rawRoot);
            if (!Files.isDirectory(root)) {
                return SystemHealthItemResponse.of("graphrag-output", false, false, "graphrag root missing");
            }

            Path outputDir = root.resolve("output");
            Path lanceDbDir = outputDir.resolve("lancedb");
            boolean outputExists = Files.isDirectory(outputDir);
            boolean lanceDbExists = Files.isDirectory(lanceDbDir);

            if (outputExists && lanceDbExists) {
                return SystemHealthItemResponse.of("graphrag-output", true, true, "output and lancedb directories exist");
            }

            String message = buildGraphRagOutputMessage(outputExists, lanceDbExists);
            return SystemHealthItemResponse.of("graphrag-output", true, false, message);
        } catch (Exception exception) {
            return SystemHealthItemResponse.of("graphrag-output", false, false, exception.getMessage());
        }
    }

    private String buildGraphRagOutputMessage(boolean outputExists, boolean lanceDbExists) {
        if (!outputExists) {
            return "output directory missing";
        }
        if (!lanceDbExists) {
            return "lancedb directory missing";
        }
        return "output artifacts not ready";
    }
}
