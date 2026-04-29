package org.ysu.ckqaback.integration.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CkqaIntegrationPropertiesTest {

    @Test
    void shouldResolveMeasuredDefaultQueryTaskPoliciesByMode() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();

        assertThat(properties.resolveQueryTaskModePolicy("local").recommendedPollingIntervalSeconds()).isEqualTo(10L);
        assertThat(properties.resolveQueryTaskModePolicy("local").staleTimeoutSeconds()).isEqualTo(300L);

        assertThat(properties.resolveQueryTaskModePolicy("global").recommendedPollingIntervalSeconds()).isEqualTo(30L);
        assertThat(properties.resolveQueryTaskModePolicy("global").staleTimeoutSeconds()).isEqualTo(1800L);
        assertThat(properties.resolveQueryTaskModePolicy("global").timeoutMessage()).contains("10 到 20 分钟");

        assertThat(properties.resolveQueryTaskModePolicy("drift").recommendedPollingIntervalSeconds()).isEqualTo(30L);
        assertThat(properties.resolveQueryTaskModePolicy("drift").staleTimeoutSeconds()).isEqualTo(1800L);
        assertThat(properties.resolveQueryTaskModePolicy("drift").timeoutMessage()).contains("10 到 20 分钟");
    }

    @Test
    void shouldKeepManagedGraphRagApiOptInForDevelopment() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();

        assertThat(properties.getGraphrag().getManagedApi().isEnabled()).isFalse();
        assertThat(properties.getGraphrag().getManagedApi().getCondaEnv()).isEqualTo("graphrag-oneapi");
        assertThat(properties.getGraphrag().getManagedApi().getHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getGraphrag().getManagedApi().getPort()).isEqualTo(8012);
    }
}
