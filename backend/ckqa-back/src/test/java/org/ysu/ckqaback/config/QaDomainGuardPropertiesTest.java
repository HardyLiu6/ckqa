package org.ysu.ckqaback.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QaDomainGuardPropertiesTest {

    @Test
    void shouldDefaultToEnabledWithConservativeThreshold() {
        QaDomainGuardProperties properties = new QaDomainGuardProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getOutOfScopeThreshold()).isEqualTo(0.25D);
    }
}
