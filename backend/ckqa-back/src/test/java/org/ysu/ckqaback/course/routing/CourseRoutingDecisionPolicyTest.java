package org.ysu.ckqaback.course.routing;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.course.dto.CourseRoutingCandidateResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRoutingDecisionPolicyTest {

    @Test
    void shouldAutoMatchWhenScoreAndMarginReachThreshold() {
        CourseRoutingDecisionPolicy policy = new CourseRoutingDecisionPolicy(0.65D, 0.08D);

        CourseRoutingDecision decision = policy.decide(List.of(
                CourseRoutingCandidateResponse.of("os", "操作系统", 0.74D, "课程画像相似度 0.740"),
                CourseRoutingCandidateResponse.of("ds", "数据结构", 0.62D, "课程画像相似度 0.620")
        ));

        assertThat(decision.status()).isEqualTo("matched");
        assertThat(decision.selectedCourseId()).isEqualTo("os");
        assertThat(decision.confidence()).isEqualTo(0.74D);
        assertThat(decision.margin()).isEqualTo(0.12D);
    }

    @Test
    void shouldNeedConfirmationWhenMarginIsTooSmall() {
        CourseRoutingDecisionPolicy policy = new CourseRoutingDecisionPolicy(0.65D, 0.08D);

        CourseRoutingDecision decision = policy.decide(List.of(
                CourseRoutingCandidateResponse.of("os", "操作系统", 0.70D, "课程画像相似度 0.700"),
                CourseRoutingCandidateResponse.of("ds", "数据结构", 0.66D, "课程画像相似度 0.660")
        ));

        assertThat(decision.status()).isEqualTo("needs_confirmation");
        assertThat(decision.selectedCourseId()).isNull();
        assertThat(decision.confidence()).isEqualTo(0.70D);
        assertThat(decision.margin()).isEqualTo(0.04D);
    }

    @Test
    void shouldReturnNoMatchWhenTopScoreIsBelowThreshold() {
        CourseRoutingDecisionPolicy policy = new CourseRoutingDecisionPolicy(0.65D, 0.08D);

        CourseRoutingDecision decision = policy.decide(List.of(
                CourseRoutingCandidateResponse.of("os", "操作系统", 0.30D, "课程画像相似度 0.300"),
                CourseRoutingCandidateResponse.of("ds", "数据结构", 0.20D, "课程画像相似度 0.200")
        ));

        assertThat(decision.status()).isEqualTo("no_match");
        assertThat(decision.selectedCourseId()).isNull();
        assertThat(decision.confidence()).isEqualTo(0.30D);
        assertThat(decision.margin()).isEqualTo(0.10D);
    }

    @Test
    void shouldReturnNoMatchWithoutCandidates() {
        CourseRoutingDecisionPolicy policy = new CourseRoutingDecisionPolicy(0.65D, 0.08D);

        CourseRoutingDecision decision = policy.decide(List.of());

        assertThat(decision.status()).isEqualTo("no_match");
        assertThat(decision.selectedCourseId()).isNull();
        assertThat(decision.confidence()).isEqualTo(0.0D);
        assertThat(decision.margin()).isEqualTo(0.0D);
    }
}
