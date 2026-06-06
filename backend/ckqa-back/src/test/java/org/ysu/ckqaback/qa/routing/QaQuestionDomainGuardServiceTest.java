package org.ysu.ckqaback.qa.routing;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.config.QaDomainGuardProperties;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider;
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider.ScopeRelevance;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckRequest;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class QaQuestionDomainGuardServiceTest {

    @Test
    void shouldBlockWhenCourseRelevanceBelowThreshold() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        given(provider.evaluateScopeRelevance(eq("os"), any())).willReturn(ScopeRelevance.evaluated(0.08D));
        QaQuestionDomainGuardService service = serviceWithProvider(provider, 0.20D);

        QaQuestionDomainCheckRequest request = request("今天晚上吃什么");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("out_of_scope");
        assertThat(response.getReasonCode()).isEqualTo("low_course_relevance");
        assertThat(response.getStrategy()).isEqualTo("semantic_relevance_v1");
    }

    @Test
    void shouldBlockLowRelevanceOffTopicConceptWithDefaultThreshold() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        given(provider.evaluateScopeRelevance(eq("os"), any())).willReturn(ScopeRelevance.evaluated(0.329692D));
        QaQuestionDomainGuardService service = serviceWithProvider(provider, new QaDomainGuardProperties());

        QaQuestionDomainCheckRequest request = request("什么是二叉树");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("out_of_scope");
        assertThat(response.getReasonCode()).isEqualTo("low_course_relevance");
    }

    @Test
    void shouldAllowDeadlockDefinitionWithDefaultThreshold() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        given(provider.evaluateScopeRelevance(eq("os"), any())).willReturn(ScopeRelevance.evaluated(0.371970D));
        QaQuestionDomainGuardService service = serviceWithProvider(provider, new QaDomainGuardProperties());

        QaQuestionDomainCheckRequest request = request("什么是死锁？");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
    }

    @Test
    void shouldAllowWhenCourseRelevanceAtOrAboveThreshold() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        given(provider.evaluateScopeRelevance(eq("os"), any())).willReturn(ScopeRelevance.evaluated(0.42D));
        QaQuestionDomainGuardService service = serviceWithProvider(provider, 0.20D);

        QaQuestionDomainCheckRequest request = request("请解释银行家算法的安全性检查");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
        assertThat(response.getStrategy()).isEqualTo("semantic_relevance_v1");
    }

    @Test
    void shouldAllowWithoutEvaluatingWhenDomainGuardDisabled() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        QaDomainGuardProperties properties = new QaDomainGuardProperties();
        properties.setEnabled(false);
        properties.setOutOfScopeThreshold(0.33D);
        QaQuestionDomainGuardService service = serviceWithProvider(provider, properties);

        QaQuestionDomainCheckRequest request = request("今天晚上吃什么");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
        then(provider).should(never()).evaluateScopeRelevance(any(), any());
    }

    @Test
    void shouldAllowFollowUpWithoutEvaluating() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        QaQuestionDomainGuardService service = serviceWithProvider(provider, 0.20D);

        QaQuestionDomainCheckRequest request = request("它和资源分配图有什么关系？");
        request.setCourseId("os");
        request.setHasConversationContext(true);
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
        then(provider).should(never()).evaluateScopeRelevance(any(), any());
    }

    @Test
    void shouldAllowWhenRelevanceNotEvaluated() {
        CourseScopeRelevanceProvider provider = mock(CourseScopeRelevanceProvider.class);
        given(provider.evaluateScopeRelevance(any(), any())).willReturn(ScopeRelevance.notEvaluated());
        QaQuestionDomainGuardService service = serviceWithProvider(provider, 0.20D);

        QaQuestionDomainCheckRequest request = request("今天晚上吃什么");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
    }

    @Test
    void shouldAllowWhenRelevanceProviderNotWired() {
        QaQuestionDomainGuardService service = serviceWithoutProvider();

        QaQuestionDomainCheckRequest request = request("今天晚上吃什么");
        request.setCourseId("os");
        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
    }

    @Test
    void shouldValidateSessionOwnerAndSessionScope() {
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(sessionsService, knowledgeBasesService);
        service.setCourseAccessService(courseAccessService);

        QaSessions session = session(21L, 7L, "os", 5L);
        given(sessionsService.getRequiredById(21L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(knowledgeBase(5L, "os"));

        QaQuestionDomainCheckRequest request = request("什么是进程？");
        request.setCourseId("os");
        request.setKnowledgeBaseId(5L);
        request.setSessionId(21L);

        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
        then(courseAccessService).should().assertCourseReadable("os", "student.zhouzh");
    }

    @Test
    void shouldRejectSessionThatBelongsToAnotherUser() {
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(sessionsService, mock(KnowledgeBasesService.class));
        given(sessionsService.getRequiredById(21L)).willReturn(session(21L, 8L, "os", 5L));

        QaQuestionDomainCheckRequest request = request("什么是进程？");
        request.setSessionId(21L);

        assertThatThrownBy(() -> service.check(request, student()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能访问自己的问答会话");
    }

    @Test
    void shouldRejectKnowledgeBaseOutsideRequestedCourse() {
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(mock(QaSessionsService.class), knowledgeBasesService);
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(knowledgeBase(5L, "db"));

        QaQuestionDomainCheckRequest request = request("什么是进程？");
        request.setCourseId("os");
        request.setKnowledgeBaseId(5L);

        assertThatThrownBy(() -> service.check(request, student()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不属于当前课程");
    }

    @Test
    void shouldValidateRequestKnowledgeBaseWhenSessionHasNoKnowledgeBase() {
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(sessionsService, knowledgeBasesService);
        service.setCourseAccessService(courseAccessService);
        given(sessionsService.getRequiredById(21L)).willReturn(session(21L, 7L, "os", null));
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(knowledgeBase(5L, "os"));

        QaQuestionDomainCheckRequest request = request("什么是进程？");
        request.setSessionId(21L);
        request.setCourseId("os");
        request.setKnowledgeBaseId(5L);

        var response = service.check(request, student());

        assertThat(response.getStatus()).isEqualTo("allowed");
        then(knowledgeBasesService).should().getRequiredById(5L);
        then(courseAccessService).should().assertCourseReadable("os", "student.zhouzh");
    }

    @Test
    void shouldRejectRequestKnowledgeBaseOutsideSessionCourseWhenSessionHasNoKnowledgeBase() {
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(sessionsService, knowledgeBasesService);
        given(sessionsService.getRequiredById(21L)).willReturn(session(21L, 7L, "os", null));
        given(knowledgeBasesService.getRequiredById(5L)).willReturn(knowledgeBase(5L, "db"));

        QaQuestionDomainCheckRequest request = request("什么是进程？");
        request.setSessionId(21L);
        request.setCourseId("os");
        request.setKnowledgeBaseId(5L);

        assertThatThrownBy(() -> service.check(request, student()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不属于当前课程");
    }

    @Test
    void shouldRequireAuthenticatedUser() {
        assertThatThrownBy(() -> serviceWithoutProvider().check(request("什么是进程？"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先登录");
    }

    private QaQuestionDomainGuardService serviceWithProvider(CourseScopeRelevanceProvider provider, double threshold) {
        QaDomainGuardProperties properties = new QaDomainGuardProperties();
        properties.setOutOfScopeThreshold(threshold);
        return serviceWithProvider(provider, properties);
    }

    private QaQuestionDomainGuardService serviceWithProvider(
            CourseScopeRelevanceProvider provider,
            QaDomainGuardProperties properties
    ) {
        QaQuestionDomainGuardService service = new QaQuestionDomainGuardService(
                mock(QaSessionsService.class), mock(KnowledgeBasesService.class));
        service.setRelevanceProvider(provider);
        service.setProperties(properties);
        return service;
    }

    private QaQuestionDomainGuardService serviceWithoutProvider() {
        return new QaQuestionDomainGuardService(mock(QaSessionsService.class), mock(KnowledgeBasesService.class));
    }

    private QaQuestionDomainCheckRequest request(String question) {
        QaQuestionDomainCheckRequest request = new QaQuestionDomainCheckRequest();
        request.setQuestion(question);
        return request;
    }

    private QaSessions session(Long id, Long userId, String courseId, Long knowledgeBaseId) {
        QaSessions session = new QaSessions();
        session.setId(id);
        session.setUserId(userId);
        session.setCourseId(courseId);
        session.setKnowledgeBaseId(knowledgeBaseId);
        return session;
    }

    private KnowledgeBases knowledgeBase(Long id, String courseId) {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(id);
        knowledgeBase.setCourseId(courseId);
        return knowledgeBase;
    }

    private AuthenticatedUser student() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
