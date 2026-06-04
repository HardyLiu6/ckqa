package org.ysu.ckqaback;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CkqaBackApplicationDevtoolsTest {

    private final String previousValue = System.getProperty(CkqaBackApplication.DEVTOOLS_RESTART_ENABLED_PROPERTY);

    @AfterEach
    void restoreSystemProperty() {
        if (previousValue == null) {
            System.clearProperty(CkqaBackApplication.DEVTOOLS_RESTART_ENABLED_PROPERTY);
            return;
        }
        System.setProperty(CkqaBackApplication.DEVTOOLS_RESTART_ENABLED_PROPERTY, previousValue);
    }

    @Test
    void shouldDisableDevtoolsRestartByDefault() {
        System.clearProperty(CkqaBackApplication.DEVTOOLS_RESTART_ENABLED_PROPERTY);

        CkqaBackApplication.configureDevtoolsRestartDefault(Map.of());

        assertThat(System.getProperty(CkqaBackApplication.DEVTOOLS_RESTART_ENABLED_PROPERTY)).isEqualTo("false");
    }

    @Test
    void shouldRespectExplicitSystemProperty() {
        System.setProperty(CkqaBackApplication.DEVTOOLS_RESTART_ENABLED_PROPERTY, "true");

        CkqaBackApplication.configureDevtoolsRestartDefault(Map.of(CkqaBackApplication.CKQA_DEVTOOLS_RESTART_ENABLED_ENV, "false"));

        assertThat(System.getProperty(CkqaBackApplication.DEVTOOLS_RESTART_ENABLED_PROPERTY)).isEqualTo("true");
    }

    @Test
    void shouldAllowCkqaEnvironmentOptIn() {
        System.clearProperty(CkqaBackApplication.DEVTOOLS_RESTART_ENABLED_PROPERTY);

        CkqaBackApplication.configureDevtoolsRestartDefault(Map.of(CkqaBackApplication.CKQA_DEVTOOLS_RESTART_ENABLED_ENV, "true"));

        assertThat(System.getProperty(CkqaBackApplication.DEVTOOLS_RESTART_ENABLED_PROPERTY)).isEqualTo("true");
    }
}
