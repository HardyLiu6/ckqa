package org.ysu.ckqaback.qa.memory;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaLearningMemories;
import org.ysu.ckqaback.entity.QaMemoryPreferences;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.memory.dto.UpdateQaMemoryPreferenceRequest;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaLearningMemoriesService;
import org.ysu.ckqaback.service.QaMemoryPreferencesService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class QaMemoryServiceTest {

    @Test
    void shouldReturnDefaultDisabledPreferenceForReadableScope() {
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaMemoryService service = service(preferencesService, mock(QaLearningMemoriesService.class), readableCourse(), knowledgeBase());

        var response = service.getPreferences("os", 3L, student());

        assertThat(response.getEnabled()).isFalse();
        assertThat(response.getIndexRunId()).isEqualTo(17L);
        then(preferencesService).should().findByScope(7L, "os", 3L, 17L);
    }

    @Test
    void shouldRejectPreferenceUpdateWhenCourseIsNotReadable() {
        CourseAccessService courseAccessService = readableCourse();
        doThrow(new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.FORBIDDEN, "无课程访问权限"))
                .when(courseAccessService).assertCourseReadable("os", "student.zhouzh");
        QaMemoryService service = service(mock(QaMemoryPreferencesService.class), mock(QaLearningMemoriesService.class), courseAccessService, knowledgeBase());

        UpdateQaMemoryPreferenceRequest request = new UpdateQaMemoryPreferenceRequest();
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        request.setEnabled(true);

        assertThatThrownBy(() -> service.updatePreferences(request, student()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getMessage()).contains("无课程访问权限");
                });
    }

    @Test
    void shouldRejectPreferenceUpdateWhenKnowledgeBaseDoesNotBelongToCourse() {
        KnowledgeBases knowledgeBase = knowledgeBase();
        knowledgeBase.setCourseId("database");
        QaMemoryService service = service(mock(QaMemoryPreferencesService.class), mock(QaLearningMemoriesService.class), readableCourse(), knowledgeBase);

        UpdateQaMemoryPreferenceRequest request = new UpdateQaMemoryPreferenceRequest();
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        request.setEnabled(true);

        assertThatThrownBy(() -> service.updatePreferences(request, student()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识库不属于当前课程");
    }

    @Test
    void shouldUpsertPreferenceUsingActiveIndexRunId() {
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaMemoryPreferences preference = new QaMemoryPreferences();
        preference.setUserId(7L);
        preference.setCourseId("os");
        preference.setKnowledgeBaseId(3L);
        preference.setIndexRunId(17L);
        preference.setEnabled(true);
        given(preferencesService.upsertPreference(7L, "os", 3L, 17L, true)).willReturn(preference);
        QaMemoryService service = service(preferencesService, mock(QaLearningMemoriesService.class), readableCourse(), knowledgeBase());

        UpdateQaMemoryPreferenceRequest request = new UpdateQaMemoryPreferenceRequest();
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        request.setEnabled(true);

        var response = service.updatePreferences(request, student());

        assertThat(response.getEnabled()).isTrue();
        assertThat(response.getIndexRunId()).isEqualTo(17L);
    }

    @Test
    void shouldListItemsWithoutMemoryText() {
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaLearningMemories memory = new QaLearningMemories();
        memory.setId(101L);
        memory.setUserId(7L);
        memory.setCourseId("os");
        memory.setKnowledgeBaseId(3L);
        memory.setIndexRunId(17L);
        memory.setMemoryType("learning_topic");
        memory.setMemoryText("不应该出现在响应里");
        memory.setStatus("active");
        given(memoriesService.listActiveByScope(7L, "os", 3L, 17L, 100)).willReturn(List.of(memory));
        QaMemoryService service = service(mock(QaMemoryPreferencesService.class), memoriesService, readableCourse(), knowledgeBase());

        var response = service.listItems("os", 3L, student());

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(101L);
        assertThat(response.get(0).getMemoryType()).isEqualTo("learning_topic");
    }

    @Test
    void shouldSoftDeleteOnlyCurrentUsersMemory() {
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryService service = service(mock(QaMemoryPreferencesService.class), memoriesService, readableCourse(), knowledgeBase());

        service.deleteItem(101L, student());

        then(memoriesService).should().softDeleteForUser(101L, 7L);
    }

    private QaMemoryService service(
            QaMemoryPreferencesService preferencesService,
            QaLearningMemoriesService memoriesService,
            CourseAccessService courseAccessService,
            KnowledgeBases knowledgeBase
    ) {
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(knowledgeBase);
        return new QaMemoryService(preferencesService, memoriesService, knowledgeBasesService, courseAccessService);
    }

    private CourseAccessService readableCourse() {
        return mock(CourseAccessService.class);
    }

    private KnowledgeBases knowledgeBase() {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(3L);
        knowledgeBase.setCourseId("os");
        knowledgeBase.setActiveIndexRunId(17L);
        return knowledgeBase;
    }

    private AuthenticatedUser student() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
