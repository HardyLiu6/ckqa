package org.ysu.ckqaback.integration.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CkqaIntegrationPropertiesTest {

    @Test
    void shouldResolveMeasuredDefaultQueryTaskPoliciesByMode() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();

        assertThat(properties.resolveQueryTaskModePolicy("local").recommendedPollingIntervalSeconds()).isEqualTo(10L);
        assertThat(properties.resolveQueryTaskModePolicy("local").staleTimeoutSeconds()).isEqualTo(300L);

        assertThat(properties.resolveQueryTaskModePolicy("global").recommendedPollingIntervalSeconds()).isEqualTo(30L);
        assertThat(properties.resolveQueryTaskModePolicy("global").staleTimeoutSeconds()).isEqualTo(1800L);
        assertThat(properties.resolveQueryTaskModePolicy("global").timeoutMessage())
                .contains("汇总课程报告")
                .contains("尽量保留已生成内容")
                .doesNotContain("10 到 20 分钟")
                .doesNotContain("前端低频轮询");

        assertThat(properties.resolveQueryTaskModePolicy("drift").recommendedPollingIntervalSeconds()).isEqualTo(30L);
        assertThat(properties.resolveQueryTaskModePolicy("drift").staleTimeoutSeconds()).isEqualTo(1800L);
        assertThat(properties.resolveQueryTaskModePolicy("drift").timeoutMessage())
                .contains("追问检索")
                .contains("尽量保留已生成内容")
                .doesNotContain("10 到 20 分钟")
                .doesNotContain("前端低频轮询");

        assertThat(properties.resolveQueryTaskModePolicy("hybrid_v0").recommendedPollingIntervalSeconds()).isEqualTo(30L);
        assertThat(properties.resolveQueryTaskModePolicy("hybrid_v0").staleTimeoutSeconds()).isEqualTo(1800L);
        assertThat(properties.resolveQueryTaskModePolicy("hybrid_v0").timeoutMessage()).contains("混合检索");
    }

    @Test
    void shouldKeepApplicationDefaultTimeoutMessagesStudentFacing() throws Exception {
        List<String> timeoutMessageLines = Files.readAllLines(Path.of("src/main/resources/application.properties"))
                .stream()
                .filter(line -> line.contains("query-task-mode-timeout-messages."))
                .toList();

        assertThat(timeoutMessageLines).isNotEmpty();
        assertThat(String.join("\n", timeoutMessageLines))
                .doesNotContain("实测")
                .doesNotContain("10 \\u5230 20 \\u5206\\u949f")
                .doesNotContain("前端")
                .doesNotContain("\\u524d\\u7aef")
                .doesNotContain("低频轮询")
                .doesNotContain("\\u4f4e\\u9891\\u8f6e\\u8be2")
                .doesNotContain("stale");
    }

    @Test
    void shouldLoadApplicationDefaultTimeoutMessagesAsReadableChinese() throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(inputStream);
        }

        assertThat(properties.getProperty("ckqa.integration.timeout.query-task-mode-timeout-messages.basic"))
                .contains("basic 模式正在检索课程片段并生成回答")
                .contains("尽量保留已生成内容")
                .doesNotContain("æ");
    }

    @Test
    void shouldKeepManagedGraphRagApiOptInForDevelopment() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();

        assertThat(properties.getGraphrag().getManagedApi().isEnabled()).isFalse();
        assertThat(properties.getGraphrag().getManagedApi().getCondaEnv()).isEqualTo("graphrag-oneapi");
        assertThat(properties.getGraphrag().getManagedApi().getHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getGraphrag().getManagedApi().getPort()).isEqualTo(8012);
        assertThat(properties.getGraphrag().isConcurrentBuildsEnabled()).isTrue();
        assertThat(properties.getGraphrag().getAutoActivationPolicy()).isEqualTo("latest-build-only");
        assertThat(properties.getGraphrag().getRetention().getKeepSuccessBuildRuns()).isEqualTo(3);
        assertThat(properties.getGraphrag().getRetention().getKeepFailedBuildRuns()).isEqualTo(3);
        assertThat(properties.getGraphrag().getRetention().isAutoCleanupEnabled()).isFalse();
    }

    @Test
    void shouldEnablePythonStreamingForEverySupportedQaModeByDefault() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();

        assertThat(properties.getStreaming().isPythonStreamModeEnabled("basic")).isTrue();
        assertThat(properties.getStreaming().isPythonStreamModeEnabled("local")).isTrue();
        assertThat(properties.getStreaming().isPythonStreamModeEnabled("global")).isTrue();
        assertThat(properties.getStreaming().isPythonStreamModeEnabled("drift")).isTrue();
        assertThat(properties.getStreaming().isPythonStreamModeEnabled("hybrid_v0")).isTrue();
        assertThat(properties.getStreaming().isPythonStreamModeEnabled("unknown")).isFalse();
    }
}
