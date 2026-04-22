package org.ysu.ckqaback.integration.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
    }

    @Getter
    @Setter
    public static class PollingProperties {
        private long queryTaskIntervalSeconds = 5L;
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
        private long queryTaskStaleSeconds = 30L;
    }
}
