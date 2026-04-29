package org.ysu.ckqaback.integration.graphrag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties.ManagedApiProperties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 开发态 GraphRAG API 托管器。
 * <p>
 * 该组件只在显式开启 {@code ckqa.integration.graphrag.managed-api.enabled} 时启动
 * {@code graphrag_pipeline/utils/main.py}。生产或手工调试场景仍可继续外部独立启动
 * GraphRAG API，避免 Java 后端与 Python 服务形成硬耦合。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ManagedGraphRagApiServer implements ApplicationRunner, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ManagedGraphRagApiServer.class);

    private final CkqaIntegrationProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ExecutorService logExecutor = Executors.newFixedThreadPool(2);

    private Process process;

    @Override
    public void run(ApplicationArguments args) throws IOException {
        ManagedApiProperties managedApi = properties.getGraphrag().getManagedApi();
        if (!managedApi.isEnabled()) {
            return;
        }
        if (!hasText(properties.getGraphrag().getRoot())) {
            log.warn("已开启 GraphRAG API 托管启动，但 GRAPHRAG_ROOT 为空，跳过自动启动。");
            return;
        }
        if (managedApi.isSkipIfReachable() && isApiReachable()) {
            log.info("GraphRAG API 已可访问，跳过托管启动: {}", properties.getGraphrag().getApiBaseUrl());
            return;
        }

        StartPlan plan = buildStartPlan();
        ProcessBuilder builder = new ProcessBuilder(plan.command());
        builder.directory(plan.workDir().toFile());
        builder.environment().putAll(plan.environment());

        process = builder.start();
        pipeLog(process.getInputStream(), "stdout");
        pipeLog(process.getErrorStream(), "stderr");

        log.info("已托管启动 GraphRAG API: {} (workDir={})", String.join(" ", plan.command()), plan.workDir());
    }

    StartPlan buildStartPlan() {
        Path root = Path.of(properties.getGraphrag().getRoot());
        ManagedApiProperties managedApi = properties.getGraphrag().getManagedApi();
        Path outputDir = resolvePath(root, managedApi.getOutputDir(), "output");
        Path storageDir = resolvePath(root, managedApi.getStorageDir(), outputDir.toString());
        Path lancedbUri = resolvePath(root, managedApi.getLancedbUri(), outputDir.resolve("lancedb").toString());

        List<String> command = new ArrayList<>();
        if (hasText(properties.getGraphrag().getPython())) {
            command.add(properties.getGraphrag().getPython());
        } else {
            command.add("conda");
            command.add("run");
            command.add("-n");
            command.add(managedApi.getCondaEnv());
            command.add("python");
        }
        command.add("utils/main.py");

        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("GRAPHRAG_API_HOST", managedApi.getHost());
        environment.put("GRAPHRAG_API_PORT", String.valueOf(managedApi.getPort()));
        environment.put("GRAPHRAG_OUTPUT_DIR", outputDir.toString());
        environment.put("GRAPHRAG_STORAGE_DIR", storageDir.toString());
        environment.put("GRAPHRAG_LANCEDB_URI", lancedbUri.toString());

        return new StartPlan(command, root, environment);
    }

    private Path resolvePath(Path root, String configured, String defaultValue) {
        String value = hasText(configured) ? configured : defaultValue;
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path;
        }
        return root.resolve(path).normalize();
    }

    private boolean isApiReachable() {
        if (!hasText(properties.getGraphrag().getApiBaseUrl())) {
            return false;
        }
        try {
            restClientBuilder.build()
                    .get()
                    .uri(properties.getGraphrag().getApiBaseUrl() + "/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception exception) {
            log.debug("GraphRAG API 尚不可访问，将尝试托管启动: {}", exception.getMessage());
            return false;
        }
    }

    private void pipeLog(InputStream inputStream, String streamName) {
        logExecutor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[graphrag-api:{}] {}", streamName, line);
                }
            } catch (IOException exception) {
                log.debug("读取 GraphRAG API {} 日志结束: {}", streamName, exception.getMessage());
            }
        });
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public void destroy() {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                Duration timeout = Duration.ofSeconds(properties.getGraphrag().getManagedApi().getStopTimeoutSeconds());
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        logExecutor.shutdownNow();
    }

    public record StartPlan(
            List<String> command,
            Path workDir,
            Map<String, String> environment
    ) {
    }
}
