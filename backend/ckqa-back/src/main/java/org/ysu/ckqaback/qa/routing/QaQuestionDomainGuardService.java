package org.ysu.ckqaback.qa.routing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.config.QaDomainGuardProperties;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider;
import org.ysu.ckqaback.course.routing.CourseScopeRelevanceProvider.ScopeRelevance;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckRequest;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckResponse;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.util.Objects;

/**
 * 问题领域硬拦截。
 * <p>
 * 复用课程画像语义相关性判断问题是否属于当前课程资料问答范围；
 * 追问、未接线、服务故障一律放行，避免误伤真问题。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaQuestionDomainGuardService {

    private static final String OUT_OF_SCOPE_MESSAGE =
            "这个问题好像不在当前课程的资料范围内，换成课程里的概念、章节或知识点再试试吧。";

    private final QaSessionsService qaSessionsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private CourseAccessService courseAccessService;
    private CourseScopeRelevanceProvider relevanceProvider;
    private QaDomainGuardProperties properties = new QaDomainGuardProperties();

    @Autowired(required = false)
    public void setCourseAccessService(CourseAccessService courseAccessService) {
        this.courseAccessService = courseAccessService;
    }

    @Autowired(required = false)
    public void setRelevanceProvider(CourseScopeRelevanceProvider relevanceProvider) {
        this.relevanceProvider = relevanceProvider;
    }

    @Autowired(required = false)
    public void setProperties(QaDomainGuardProperties properties) {
        if (properties != null) {
            this.properties = properties;
        }
    }

    public QaQuestionDomainCheckResponse check(QaQuestionDomainCheckRequest request, AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        String courseId = resolveScopeCourseId(request, currentUser);
        return classify(courseId, request.getQuestion(), Boolean.TRUE.equals(request.getHasConversationContext()));
    }

    private QaQuestionDomainCheckResponse classify(String courseId, String question, boolean hasContext) {
        if (hasContext) {
            return QaQuestionDomainCheckResponse.allowed();
        }
        if (!properties.isEnabled()) {
            return QaQuestionDomainCheckResponse.allowed();
        }
        if (relevanceProvider == null) {
            return QaQuestionDomainCheckResponse.allowed();
        }
        ScopeRelevance relevance = relevanceProvider.evaluateScopeRelevance(courseId, question);
        boolean outOfScope = relevance.evaluated()
                && relevance.confidence() < properties.getOutOfScopeThreshold();
        log.info("qa_domain_guard courseId={} questionHash={} evaluated={} confidence={} threshold={} decision={}",
                courseId,
                Integer.toHexString(Objects.hashCode(question)),
                relevance.evaluated(),
                relevance.confidence(),
                properties.getOutOfScopeThreshold(),
                outOfScope ? "out_of_scope" : "allowed");
        if (outOfScope) {
            return QaQuestionDomainCheckResponse.outOfScope("low_course_relevance", OUT_OF_SCOPE_MESSAGE);
        }
        return QaQuestionDomainCheckResponse.allowed();
    }

    private String resolveScopeCourseId(QaQuestionDomainCheckRequest request, AuthenticatedUser currentUser) {
        if (request.getSessionId() != null) {
            QaSessions session = qaSessionsService.getRequiredById(request.getSessionId());
            if (!currentUser.id().equals(session.getUserId())) {
                throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "只能访问自己的问答会话");
            }
            validateSessionRequestScope(request, session);
            String courseId = effectiveSessionCourseId(request, session);
            if (session.getKnowledgeBaseId() != null) {
                KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(session.getKnowledgeBaseId());
                validateKnowledgeBaseScope(courseId, knowledgeBase, currentUser);
                courseId = firstNonBlank(courseId, knowledgeBase.getCourseId());
            } else if (request.getKnowledgeBaseId() != null) {
                KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(request.getKnowledgeBaseId());
                validateKnowledgeBaseScope(courseId, knowledgeBase, currentUser);
                courseId = firstNonBlank(courseId, knowledgeBase.getCourseId());
            } else if (StringUtils.hasText(courseId)) {
                validateCourseReadable(courseId, currentUser);
            }
            return courseId;
        }

        if (request.getKnowledgeBaseId() != null) {
            KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(request.getKnowledgeBaseId());
            validateKnowledgeBaseScope(request.getCourseId(), knowledgeBase, currentUser);
            return firstNonBlank(request.getCourseId(), knowledgeBase.getCourseId());
        }

        if (StringUtils.hasText(request.getCourseId())) {
            validateCourseReadable(request.getCourseId(), currentUser);
            return request.getCourseId();
        }
        return null;
    }

    private String firstNonBlank(String primary, String secondary) {
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        return StringUtils.hasText(secondary) ? secondary : null;
    }

    private void validateSessionRequestScope(QaQuestionDomainCheckRequest request, QaSessions session) {
        if (StringUtils.hasText(request.getCourseId())
                && StringUtils.hasText(session.getCourseId())
                && !Objects.equals(request.getCourseId(), session.getCourseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "会话不属于当前课程");
        }
        if (request.getKnowledgeBaseId() != null
                && session.getKnowledgeBaseId() != null
                && !Objects.equals(request.getKnowledgeBaseId(), session.getKnowledgeBaseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "会话不属于当前知识库");
        }
    }

    private String effectiveSessionCourseId(QaQuestionDomainCheckRequest request, QaSessions session) {
        if (StringUtils.hasText(session.getCourseId())) {
            return session.getCourseId();
        }
        return request.getCourseId();
    }

    private void validateKnowledgeBaseScope(String courseId, KnowledgeBases knowledgeBase, AuthenticatedUser currentUser) {
        if (StringUtils.hasText(courseId) && !Objects.equals(courseId, knowledgeBase.getCourseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "知识库不属于当前课程");
        }
        validateCourseReadable(knowledgeBase.getCourseId(), currentUser);
    }

    private void validateCourseReadable(String courseId, AuthenticatedUser currentUser) {
        if (courseAccessService != null && StringUtils.hasText(courseId)) {
            courseAccessService.assertCourseReadable(courseId, currentUser.userCode());
        }
    }
}
