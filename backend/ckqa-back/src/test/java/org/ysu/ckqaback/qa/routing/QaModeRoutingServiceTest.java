package org.ysu.ckqaback.qa.routing;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.cache.StudentCacheKeyFactory;
import org.ysu.ckqaback.cache.StudentRedisCacheService;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.qa.dto.QaModeRecommendationRequest;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

class QaModeRoutingServiceTest {

    @Test
    void shouldRecommendBasicForDefinitionQuestion() {
        QaModeRoutingService service = buildService();
        QaModeRecommendationRequest request = request("什么是信号量？", false, false);

        var decision = service.recommend(request, student());

        assertThat(decision.getRecommendedMode()).isEqualTo("basic");
        assertThat(decision.getReasons()).contains("definition_intent");
        assertThat(decision.getConfidence()).isGreaterThan(0.6D);
    }

    @Test
    void shouldRecommendLocalForMaterialLocatorQuestion() {
        QaModeRoutingService service = buildService();
        QaModeRecommendationRequest request = request("请根据第 3 章解释银行家算法的安全性检查过程", false, false);

        var decision = service.recommend(request, student());

        assertThat(decision.getRecommendedMode()).isEqualTo("local");
        assertThat(decision.getReasons()).contains("material_locator");
    }

    @Test
    void shouldRecommendGlobalForCourseOverviewQuestion() {
        QaModeRoutingService service = buildService();
        QaModeRecommendationRequest request = request("请综述操作系统进程管理这一章的知识体系和主题脉络", false, false);

        var decision = service.recommend(request, student());

        assertThat(decision.getRecommendedMode()).isEqualTo("global");
        assertThat(decision.getReasons()).contains("summary_intent");
    }

    @Test
    void shouldRecommendDriftForExploratoryRelationQuestion() {
        QaModeRoutingService service = buildService();
        QaModeRecommendationRequest request = request("进程同步和数据库事务锁之间有什么关联，可以扩展说明吗？", false, false);

        var decision = service.recommend(request, student());

        assertThat(decision.getRecommendedMode()).isEqualTo("drift");
        assertThat(decision.getReasons()).contains("exploration_intent");
    }

    @Test
    void shouldGateHybridBehindBetaFlag() {
        QaModeRoutingService service = buildService();
        QaModeRecommendationRequest request = request("请综合比较死锁和资源分配图的关系，并给出课程证据", false, false);

        var decision = service.recommend(request, student());

        assertThat(decision.getRecommendedMode()).isNotEqualTo("hybrid_v0");
        assertThat(decision.getFallbackMode()).isEqualTo("local");
        assertThat(decision.getReasons()).contains("hybrid_beta_disabled");
        assertThat(decision.getRouteScores()).containsKey("hybrid_v0");
    }

    @Test
    void shouldMarkLowConfidenceDecisionForManualReview() {
        QaModeRoutingService service = buildService();
        QaModeRecommendationRequest request = request("请帮我复习一下。", false, false);

        var decision = service.recommend(request, student());

        assertThat(decision.getRecommendedMode()).isEqualTo("basic");
        assertThat(decision.getConfidence()).isBetween(0.50D, 0.65D);
        assertThat(decision.getConfidenceBand()).isEqualTo("low_confidence");
        assertThat(decision.getManualSwitchSuggested()).isTrue();
        assertThat(decision.getReviewPriority()).isEqualTo("low_confidence");
    }

    @Test
    void shouldRecommendHybridWhenBetaEnabledAndEvidenceRelationQuestionHasContext() {
        QaModeRoutingService service = buildService();
        QaModeRecommendationRequest request = request("它和资源分配图有什么关系？请给出材料依据", true, false);
        request.setBetaHybridEnabled(true);

        var decision = service.recommend(request, student());

        assertThat(decision.getRecommendedMode()).isEqualTo("hybrid_v0");
        assertThat(decision.getFallbackMode()).isEqualTo("local");
        assertThat(decision.getReasons()).contains("evidence_relation_intent", "follow_up_context");
    }

    @Test
    void shouldValidateSessionOwnerAndScopeWhenSessionIdIsProvided() {
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);
        QaModeRoutingService service = new QaModeRoutingService(sessionsService, knowledgeBasesService);
        service.setCourseAccessService(courseAccessService);

        QaSessions session = new QaSessions();
        session.setId(21L);
        session.setUserId(7L);
        session.setCourseId("os");
        session.setKnowledgeBaseId(5L);
        given(sessionsService.getRequiredById(21L)).willReturn(session);
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(5L);
        knowledgeBase.setCourseId("os");
        knowledgeBase.setActiveIndexRunId(9L);
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(knowledgeBase);

        QaModeRecommendationRequest request = request("它是什么意思？", true, true);
        request.setSessionId(21L);

        service.recommend(request, student());

        then(courseAccessService).should().assertCourseReadable("os", "student.zhouzh");
    }

    @Test
    void shouldUseCacheOnlyAfterScopeValidation() {
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);
        StudentRedisCacheService cacheService = mock(StudentRedisCacheService.class);
        StudentCacheKeyFactory keyFactory = mock(StudentCacheKeyFactory.class);
        QaModeRoutingService service = new QaModeRoutingService(sessionsService, knowledgeBasesService);
        service.setCourseAccessService(courseAccessService);
        service.setStudentRedisCacheService(cacheService);
        service.setStudentCacheKeyFactory(keyFactory);

        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(5L);
        knowledgeBase.setCourseId("os");
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(knowledgeBase);
        QaModeRecommendationRequest request = request("请综合比较死锁和资源分配图的关系，并给出课程证据", true, true);
        request.setBetaHybridEnabled(true);
        given(keyFactory.routingKey(7L, request, true)).willReturn("routing-key");
        given(cacheService.getOrLoad(eq("routing-key"), eq(org.ysu.ckqaback.qa.dto.QaModeRecommendationResponse.class), any(), any()))
                .willReturn(org.ysu.ckqaback.qa.dto.QaModeRecommendationResponse.of(
                        "hybrid_v0",
                        "local",
                        0.88D,
                        List.of("cached"),
                        "缓存推荐",
                        "high_confidence",
                        false,
                        "normal",
                        true,
                        true,
                        "rule_semantic_v1",
                        java.util.Map.of("hybrid_v0", 0.9D)
                ));

        var decision = service.recommend(request, student());

        assertThat(decision.getReasons()).containsExactly("cached");
        then(courseAccessService).should().assertCourseReadable("os", "student.zhouzh");
    }

    private QaModeRoutingService buildService() {
        return new QaModeRoutingService(mock(QaSessionsService.class), mock(KnowledgeBasesService.class));
    }

    private QaModeRecommendationRequest request(String question, boolean hasConversationContext, boolean scoped) {
        QaModeRecommendationRequest request = new QaModeRecommendationRequest();
        request.setQuestion(question);
        request.setHasConversationContext(hasConversationContext);
        if (scoped) {
            request.setCourseId("os");
            request.setKnowledgeBaseId(5L);
        }
        return request;
    }

    private AuthenticatedUser student() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
