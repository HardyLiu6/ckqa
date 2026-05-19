package org.ysu.ckqaback.cache;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.course.dto.CourseQueryRequest;
import org.ysu.ckqaback.qa.dto.QaModeRecommendationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class StudentCacheKeyFactoryTest {

    @Test
    void shouldGenerateStableCourseListKeyAndIsolateByUser() {
        StudentRedisCacheProperties properties = new StudentRedisCacheProperties();
        StudentCacheKeyFactory factory = new StudentCacheKeyFactory(properties);
        CourseQueryRequest request = new CourseQueryRequest();
        request.setPage(1);
        request.setSize(20);
        request.setKeyword("操作系统");
        request.setStatus("active");

        String first = factory.coursesKey("STU2026001", request);
        String second = factory.coursesKey("STU2026001", request);
        String otherUser = factory.coursesKey("STU2026002", request);

        assertThat(first).isEqualTo(second);
        assertThat(first).startsWith("ckqa:student-cache:v1:courses:user:STU2026001:");
        assertThat(otherUser).isNotEqualTo(first);
    }

    @Test
    void shouldIncludeRoutingScopeBetaAndContextInRoutingKey() {
        StudentRedisCacheProperties properties = new StudentRedisCacheProperties();
        StudentCacheKeyFactory factory = new StudentCacheKeyFactory(properties);
        QaModeRecommendationRequest request = new QaModeRecommendationRequest();
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        request.setQuestion("它和资源分配图有什么关系？");
        request.setBetaHybridEnabled(true);
        request.setHasConversationContext(true);

        String betaOn = factory.routingKey(7L, request, true);
        request.setBetaHybridEnabled(false);
        String betaOff = factory.routingKey(7L, request, true);
        String otherUser = factory.routingKey(8L, request, true);

        assertThat(betaOn).startsWith("ckqa:student-cache:v1:qa-routing:user:7:");
        assertThat(betaOff).isNotEqualTo(betaOn);
        assertThat(otherUser).isNotEqualTo(betaOff);
    }

    @Test
    void shouldIncludeKnowledgeBaseIndexAndDataDirInHybridReadinessKey() {
        StudentRedisCacheProperties properties = new StudentRedisCacheProperties();
        StudentCacheKeyFactory factory = new StudentCacheKeyFactory(properties);

        String first = factory.hybridReadinessKey(3L, 17L, "user_2/kb_3/build_9/index/output");
        String changedIndex = factory.hybridReadinessKey(3L, 18L, "user_2/kb_3/build_9/index/output");

        assertThat(first).startsWith("ckqa:student-cache:v1:hybrid-readiness:kb:3:index:17:");
        assertThat(changedIndex).isNotEqualTo(first);
    }
}
