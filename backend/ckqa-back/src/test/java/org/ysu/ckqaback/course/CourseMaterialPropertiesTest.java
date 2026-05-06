package org.ysu.ckqaback.course;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseMaterialPropertiesTest {

    @Test
    void shouldDefaultSinglePdfLimitTo200Mb() {
        CourseMaterialProperties properties = new CourseMaterialProperties();

        assertThat(properties.getMaxFileSizeBytes()).isEqualTo(200L * 1024L * 1024L);
    }
}
