package org.ysu.ckqaback.qa.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaMemoryPreferences;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.memory.dto.QaMemoryItemResponse;
import org.ysu.ckqaback.qa.memory.dto.QaMemoryPreferenceResponse;
import org.ysu.ckqaback.qa.memory.dto.UpdateQaMemoryPreferenceRequest;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaLearningMemoriesService;
import org.ysu.ckqaback.service.QaMemoryPreferencesService;

import java.util.List;
import java.util.Objects;

/**
 * 学生端长期记忆偏好与条目管理服务。
 */
@Service
@RequiredArgsConstructor
public class QaMemoryService {

    private final QaMemoryPreferencesService preferencesService;
    private final QaLearningMemoriesService memoriesService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final CourseAccessService courseAccessService;

    public QaMemoryPreferenceResponse getPreferences(String courseId, Long knowledgeBaseId, AuthenticatedUser currentUser) {
        Scope scope = resolveScope(courseId, knowledgeBaseId, currentUser);
        QaMemoryPreferences preference = preferencesService.findByScope(
                currentUser.id(),
                scope.courseId(),
                scope.knowledgeBaseId(),
                scope.indexRunId()
        );
        return QaMemoryPreferenceResponse.of(scope.courseId(), scope.knowledgeBaseId(), scope.indexRunId(),
                preference != null && Boolean.TRUE.equals(preference.getEnabled()));
    }

    public QaMemoryPreferenceResponse updatePreferences(UpdateQaMemoryPreferenceRequest request, AuthenticatedUser currentUser) {
        Scope scope = resolveScope(request.getCourseId(), request.getKnowledgeBaseId(), currentUser);
        QaMemoryPreferences preference = preferencesService.upsertPreference(
                currentUser.id(),
                scope.courseId(),
                scope.knowledgeBaseId(),
                scope.indexRunId(),
                Boolean.TRUE.equals(request.getEnabled())
        );
        return QaMemoryPreferenceResponse.of(scope.courseId(), scope.knowledgeBaseId(), scope.indexRunId(), preference.getEnabled());
    }

    public List<QaMemoryItemResponse> listItems(String courseId, Long knowledgeBaseId, AuthenticatedUser currentUser) {
        Scope scope = resolveScope(courseId, knowledgeBaseId, currentUser);
        return memoriesService.listActiveByScope(currentUser.id(), scope.courseId(), scope.knowledgeBaseId(), scope.indexRunId(), 100)
                .stream()
                .map(QaMemoryItemResponse::fromEntity)
                .toList();
    }

    public void deleteItem(Long id, AuthenticatedUser currentUser) {
        assertAuthenticated(currentUser);
        memoriesService.softDeleteForUser(id, currentUser.id());
    }

    private Scope resolveScope(String courseId, Long knowledgeBaseId, AuthenticatedUser currentUser) {
        assertAuthenticated(currentUser);
        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(knowledgeBaseId);
        if (!Objects.equals(courseId, knowledgeBase.getCourseId())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "知识库不属于当前课程");
        }
        if (knowledgeBase.getActiveIndexRunId() == null) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT);
        }
        courseAccessService.assertCourseReadable(courseId, currentUser.userCode());
        return new Scope(courseId, knowledgeBase.getId(), knowledgeBase.getActiveIndexRunId());
    }

    private void assertAuthenticated(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
    }

    private record Scope(String courseId, Long knowledgeBaseId, Long indexRunId) {
    }
}
