package org.ysu.ckqaback.course.routing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRoutingPropertiesTest {

    @Test
    void shouldDefaultToShortQuestionFriendlyThresholds() {
        CourseRoutingProperties properties = new CourseRoutingProperties();

        assertThat(properties.getScoreThreshold()).isEqualTo(0.30D);
        assertThat(properties.getDefinitionOffTopicScoreThreshold()).isEqualTo(0.33D);
        assertThat(properties.getMarginThreshold()).isEqualTo(0.06D);
    }
}
