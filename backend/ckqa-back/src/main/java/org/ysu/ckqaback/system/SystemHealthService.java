package org.ysu.ckqaback.system;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.cache.StudentRedisCacheService;
import org.ysu.ckqaback.system.dto.SystemHealthItemResponse;
import org.ysu.ckqaback.system.dto.SystemHealthResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private final org.ysu.ckqaback.graph.GraphService graphService;
    private StudentRedisCacheService studentRedisCacheService;

    @Autowired(required = false)
    public void setStudentRedisCacheService(StudentRedisCacheService studentRedisCacheService) {
        this.studentRedisCacheService = studentRedisCacheService;
    }

    public SystemHealthResponse check() {
        List<SystemHealthItemResponse> items = List.of(
                checkMySql(),
                checkRedis(),
                checkPath("pdf-ingest-root", properties.getPdfIngest().getRoot()),
                checkPath("graphrag-root", properties.getGraphrag().getRoot()),
                checkPath("graphrag-build-runs-root", resolveBuildRunsRoot()),
                checkApiReachable(),
                checkApiReady(),
                checkNeo4j()
        );
        boolean up = items.stream().allMatch(SystemHealthItemResponse::isReachable);
        return new SystemHealthResponse(up, items);
    }

    public SystemHealthResponse readiness() {
        List<SystemHealthItemResponse> items = new ArrayList<>(check().getItems());
        items.add(checkGraphRagOutput());
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

    private SystemHealthItemResponse checkRedis() {
        if (studentRedisCacheService == null) {
            return SystemHealthItemResponse.of("redis", false, false, "redis template not configured");
        }
        boolean reachable = studentRedisCacheService.ping();
        return SystemHealthItemResponse.of("redis", reachable, reachable, reachable ? "PING PONG" : "PING failed");
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

    private SystemHealthItemResponse checkNeo4j() {
        try {
            org.ysu.ckqaback.graph.GraphService.Neo4jHealth health = graphService.pingForHealth();
            return SystemHealthItemResponse.of("neo4j", health.reachable(), health.reachable(), health.message());
        } catch (Exception exception) {
            return SystemHealthItemResponse.of("neo4j", false, false, exception.getMessage());
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

    private String resolveBuildRunsRoot() {
        if (StringUtils.hasText(properties.getGraphrag().getBuildRunsRoot())) {
            return properties.getGraphrag().getBuildRunsRoot();
        }
        if (StringUtils.hasText(properties.getGraphrag().getRoot())) {
            return Path.of(properties.getGraphrag().getRoot(), "runtime", "kb-build-runs").toString();
        }
        return null;
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
