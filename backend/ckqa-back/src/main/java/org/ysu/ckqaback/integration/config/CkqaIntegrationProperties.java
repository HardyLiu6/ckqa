package org.ysu.ckqaback.integration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * CKQA 与 Python 主链路集成配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.integration")
public class CkqaIntegrationProperties {

    private final PythonProcessProperties pdfIngest = new PythonProcessProperties();
    private final GraphRagProperties graphrag = new GraphRagProperties();
    private final PollingProperties polling = new PollingProperties();
    private final TimeoutProperties timeout = new TimeoutProperties();

    public QueryTaskModePolicy resolveQueryTaskModePolicy(String rawMode) {
        String mode = normalizeMode(rawMode);
        long pollingIntervalSeconds = positiveOrDefault(
                polling.getQueryTaskModeIntervalSeconds().get(mode),
                polling.getQueryTaskIntervalSeconds()
        );
        long staleTimeoutSeconds = positiveOrDefault(
                timeout.getQueryTaskModeStaleSeconds().get(mode),
                timeout.getQueryTaskStaleSeconds()
        );
        String timeoutMessage = timeout.getQueryTaskModeTimeoutMessages().get(mode);
        if (timeoutMessage == null || timeoutMessage.isBlank()) {
            timeoutMessage = defaultTimeoutMessage(mode, staleTimeoutSeconds);
        }
        return new QueryTaskModePolicy(mode, pollingIntervalSeconds, staleTimeoutSeconds, timeoutMessage);
    }

    private String normalizeMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return "local";
        }
        return rawMode.trim().toLowerCase(Locale.ROOT);
    }

    private long positiveOrDefault(Long value, long defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    private String defaultTimeoutMessage(String mode, long staleTimeoutSeconds) {
        if ("drift".equals(mode)) {
            return "drift 模式通常耗时更长，任务心跳超过 " + staleTimeoutSeconds
                    + " 秒未更新后会被标记为 stale；可调大 QUERY_TASK_STALE_SECONDS_DRIFT 并降低前端轮询频率。";
        }
        return mode + " 模式任务心跳超过 " + staleTimeoutSeconds + " 秒未更新后会被标记为 stale。";
    }

    public record QueryTaskModePolicy(
            String mode,
            long recommendedPollingIntervalSeconds,
            long staleTimeoutSeconds,
            String timeoutMessage
    ) {
    }

    @Getter
    @Setter
    public static class PythonProcessProperties {
        private String python;
        private String root;
    }

    @Getter
    @Setter
    public static class GraphRagProperties extends PythonProcessProperties {
        private String apiBaseUrl;
        private String buildRunsRoot;
        private boolean concurrentBuildsEnabled = true;
        private String autoActivationPolicy = "latest-build-only";
        private final ManagedApiProperties managedApi = new ManagedApiProperties();
        private final RetentionProperties retention = new RetentionProperties();
    }

    @Getter
    @Setter
    public static class RetentionProperties {
        private int keepSuccessBuildRuns = 3;
        private int keepFailedBuildRuns = 3;
        private boolean autoCleanupEnabled = false;
    }

    @Getter
    @Setter
    public static class ManagedApiProperties {
        private boolean enabled = false;
        private String host = "127.0.0.1";
        private int port = 8012;
        private String condaEnv = "graphrag-oneapi";
        private String outputDir;
        private String storageDir;
        private String lancedbUri;
        private boolean skipIfReachable = true;
        private long stopTimeoutSeconds = 5L;
    }

    @Getter
    @Setter
    public static class PollingProperties {
        private long queryTaskIntervalSeconds = 10L;
        private Map<String, Long> queryTaskModeIntervalSeconds = new LinkedHashMap<>(Map.of(
                "local", 10L,
                "basic", 10L,
                "global", 30L,
                "drift", 30L
        ));
    }

    @Getter
    @Setter
    public static class TimeoutProperties {
        private long parseSeconds = 900L;
        private long exportSeconds = 300L;
        private long fetchSeconds = 300L;
        private long indexSeconds = 1800L;
        private long querySeconds = 120L;
        private long indexStaleSeconds = 2400L;
        private long queryTaskStaleSeconds = 300L;
        private Map<String, Long> queryTaskModeStaleSeconds = new LinkedHashMap<>(Map.of(
                "local", 300L,
                "basic", 300L,
                "global", 1800L,
                "drift", 1800L
        ));
        private Map<String, String> queryTaskModeTimeoutMessages = new LinkedHashMap<>(Map.of(
                "local", "local 模式实测可能需要 2 分钟左右；任务心跳超过阈值未更新后会被标记为 stale。",
                "basic", "basic 模式沿用轻量查询策略；任务心跳超过阈值未更新后会被标记为 stale。",
                "global", "global 模式实测可能需要 10 到 20 分钟；建议前端低频轮询并展示长耗时提示。",
                "drift", "drift 模式实测可能需要 10 到 20 分钟；建议前端低频轮询并展示长耗时提示。"
        ));
    }
}
