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
    private final SummaryProperties summary = new SummaryProperties();
    private final RewriteProperties rewrite = new RewriteProperties();
    private final StreamingProperties streaming = new StreamingProperties();

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
            return "drift 模式正在沿相关线索展开追问检索，等待时间较长时会尽量保留已生成内容。";
        }
        if ("hybrid_v0".equals(mode)) {
            return "混合检索 Beta 模式正在融合多路证据，等待时间较长时会尽量保留已生成内容。";
        }
        return mode + " 模式正在检索课程内容并生成回答，等待时间较长时会尽量保留已生成内容。";
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
                "drift", 30L,
                "hybrid_v0", 30L
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
        private long promptTuneSeconds = 1800L;
        private long promptTuneStaleSeconds = 2400L;
        private Map<String, Long> queryTaskModeStaleSeconds = new LinkedHashMap<>(Map.of(
                "local", 300L,
                "basic", 300L,
                "global", 1800L,
                "drift", 1800L,
                "hybrid_v0", 1800L
        ));
        private Map<String, String> queryTaskModeTimeoutMessages = new LinkedHashMap<>(Map.of(
                "local", "local 模式正在结合课程上下文生成回答，等待时间较长时会尽量保留已生成内容。",
                "basic", "basic 模式正在检索课程片段并生成回答，等待时间较长时会尽量保留已生成内容。",
                "global", "global 模式正在汇总课程报告与主题要点，等待时间较长时会尽量保留已生成内容。",
                "drift", "drift 模式正在沿相关线索展开追问检索，等待时间较长时会尽量保留已生成内容。",
                "hybrid_v0", "混合检索 Beta 模式正在融合多路证据，等待时间较长时会尽量保留已生成内容。"
        ));
    }

    @Getter
    @Setter
    public static class StreamingProperties {
        private boolean pythonStreamEnabled = true;
        private String pythonStreamModes = "basic,local,global,drift,hybrid_v0";
        private long pythonStreamConnectTimeoutSeconds = 10L;
        private long pythonStreamReadTimeoutSeconds = 300L;

        public boolean isPythonStreamModeEnabled(String rawMode) {
            if (!pythonStreamEnabled || rawMode == null || rawMode.isBlank()) {
                return false;
            }
            String mode = rawMode.trim().toLowerCase(Locale.ROOT);
            for (String item : pythonStreamModes.split(",")) {
                if (mode.equals(item.trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }

    @Getter
    @Setter
    public static class SummaryProperties {
        private boolean enabled = true;
        private String apiBaseUrl = "http://127.0.0.1:3000/v1";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        private int maxChars = 800;
        private int triggerMessageCount = 12;
        private int triggerCharCount = 3000;
        private boolean thinkingDisabled = true;
        private long timeoutSeconds = 30L;
    }

    @Getter
    @Setter
    public static class RewriteProperties {
        private boolean enabled = true;
        private String apiBaseUrl = "http://127.0.0.1:3000/v1";
        private String apiKey = "";
        private String model = "deepseek-v4-flash";
        private int maxChars = 800;
        private double minConfidence = 0.6D;
        private boolean thinkingDisabled = true;
        private long timeoutSeconds = 20L;
    }
}
