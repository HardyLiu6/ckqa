package org.ysu.ckqaback.qa.memory;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaLearningMemories;
import org.ysu.ckqaback.entity.QaMemoryPreferences;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.service.QaLearningMemoriesService;
import org.ysu.ckqaback.service.QaMemoryPreferencesService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;

class QaLearningMemoryCaptureServiceTest {

    @Test
    void shouldSkipWhenPreferenceIsDisabled() {
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaLearningMemoryCaptureService service = new QaLearningMemoryCaptureService(preferencesService, memoriesService, sessionsService);
        QaSessions session = session("formal");
        given(sessionsService.getById(5L)).willReturn(session);

        service.captureAfterAssistantSuccess(task("什么是死锁？"), assistant("死锁是资源等待。"));

        then(preferencesService).should().findByScope(7L, "os", 3L, 17L);
        then(memoriesService).should(never()).save(any(QaLearningMemories.class));
    }

    @Test
    void shouldSkipNonFormalSession() {
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaLearningMemoryCaptureService service = new QaLearningMemoryCaptureService(preferencesService, memoriesService, sessionsService);
        given(sessionsService.getById(5L)).willReturn(session("smoke"));

        service.captureAfterAssistantSuccess(task("什么是死锁？"), assistant("死锁是资源等待。"));

        then(preferencesService).should(never()).findByScope(7L, "os", 3L, 17L);
        then(memoriesService).should(never()).save(any(QaLearningMemories.class));
    }

    @Test
    void shouldCaptureTopicAndStepPreferenceFromSuccessfulQa() {
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaLearningMemoryCaptureService service = new QaLearningMemoryCaptureService(preferencesService, memoriesService, sessionsService);
        given(sessionsService.getById(5L)).willReturn(session("formal"));
        given(preferencesService.findByScope(7L, "os", 3L, 17L)).willReturn(enabledPreference());
        given(memoriesService.listActiveByScope(7L, "os", 3L, 17L, 100)).willReturn(List.of());

        service.captureAfterAssistantSuccess(task("什么是死锁？请用步骤解释"), assistant("死锁是多个进程相互等待资源。"));

        then(memoriesService).should().save(argThat(memory ->
                "learning_topic".equals(memory.getMemoryType())
                        && "学生关注：死锁".equals(memory.getMemoryText())
                        && memory.getSourceSessionId().equals(5L)
                        && memory.getSourceMessageId().equals(102L)
        ));
        then(memoriesService).should().save(argThat(memory ->
                "explanation_preference".equals(memory.getMemoryType())
                        && "偏好步骤化解释".equals(memory.getMemoryText())
        ));
    }

    @Test
    void shouldCaptureRelationshipFocusForComparisonQuestion() {
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaLearningMemoryCaptureService service = new QaLearningMemoryCaptureService(preferencesService, memoriesService, sessionsService);
        given(sessionsService.getById(5L)).willReturn(session("formal"));
        given(preferencesService.findByScope(7L, "os", 3L, 17L)).willReturn(enabledPreference());
        given(memoriesService.listActiveByScope(7L, "os", 3L, 17L, 100)).willReturn(List.of());

        service.captureAfterAssistantSuccess(task("死锁和饥饿有什么区别和关系？"), assistant("二者都与资源竞争有关。"));

        then(memoriesService).should().save(argThat(memory ->
                "unresolved_focus".equals(memory.getMemoryType())
                        && "持续关注概念关系与对比".equals(memory.getMemoryText())
        ));
    }

    @Test
    void shouldUpsertDuplicateMemoryInsteadOfSavingAgain() {
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaLearningMemoryCaptureService service = new QaLearningMemoryCaptureService(preferencesService, memoriesService, sessionsService);
        QaLearningMemories existing = memory(201L, "learning_topic", "学生关注：死锁", LocalDateTime.of(2026, 5, 1, 9, 0));
        given(sessionsService.getById(5L)).willReturn(session("formal"));
        given(preferencesService.findByScope(7L, "os", 3L, 17L)).willReturn(enabledPreference());
        given(memoriesService.listActiveByScope(7L, "os", 3L, 17L, 100)).willReturn(List.of(existing));

        service.captureAfterAssistantSuccess(task("什么是死锁？"), assistant("死锁是资源等待。"));

        then(memoriesService).should(never()).save(any(QaLearningMemories.class));
        then(memoriesService).should().updateById(argThat(memory ->
                memory.getId().equals(201L)
                        && memory.getSourceSessionId().equals(5L)
                        && memory.getSourceMessageId().equals(102L)
        ));
    }

    @Test
    void shouldSoftDeleteOldestMemoriesWhenScopeExceedsLimit() {
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaLearningMemoryCaptureService service = new QaLearningMemoryCaptureService(preferencesService, memoriesService, sessionsService);
        List<QaLearningMemories> active = new ArrayList<>();
        for (int index = 0; index < 20; index += 1) {
            active.add(memory(300L + index, "learning_topic", "学生关注：旧主题" + index, LocalDateTime.of(2026, 5, 1, 9, index)));
        }
        given(sessionsService.getById(5L)).willReturn(session("formal"));
        given(preferencesService.findByScope(7L, "os", 3L, 17L)).willReturn(enabledPreference());
        given(memoriesService.listActiveByScope(7L, "os", 3L, 17L, 100)).willReturn(active);
        given(memoriesService.save(any(QaLearningMemories.class))).willAnswer(invocation -> {
            QaLearningMemories saved = invocation.getArgument(0);
            saved.setId(999L);
            return true;
        });

        service.captureAfterAssistantSuccess(task("什么是死锁？"), assistant("死锁是资源等待。"));

        then(memoriesService).should().softDeleteForUser(eq(300L), eq(7L));
    }

    @Test
    void shouldSkipAssistantFailureAnswer() {
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaSessionsService sessionsService = mock(QaSessionsService.class);
        QaLearningMemoryCaptureService service = new QaLearningMemoryCaptureService(preferencesService, memoriesService, sessionsService);
        given(sessionsService.getById(5L)).willReturn(session("formal"));
        given(preferencesService.findByScope(7L, "os", 3L, 17L)).willReturn(enabledPreference());

        service.captureAfterAssistantSuccess(task("什么是死锁？"), assistant("抱歉，未找到相关资料，无法回答。"));

        then(memoriesService).should(never()).save(any(QaLearningMemories.class));
        then(memoriesService).should(never()).updateById(any(QaLearningMemories.class));
    }

    private QaRetrievalLogs task(String question) {
        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setUserMessageId(101L);
        task.setQueryMode("local");
        task.setOriginalQueryText(question);
        task.setQueryText(question);
        return task;
    }

    private QaMessages assistant(String content) {
        QaMessages assistant = new QaMessages();
        assistant.setId(102L);
        assistant.setContent(content);
        assistant.setRole("assistant");
        return assistant;
    }

    private QaSessions session(String sessionType) {
        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setUserId(7L);
        session.setCourseId("os");
        session.setKnowledgeBaseId(3L);
        session.setIndexRunId(17L);
        session.setSessionType(sessionType);
        return session;
    }

    private QaMemoryPreferences enabledPreference() {
        QaMemoryPreferences preference = new QaMemoryPreferences();
        preference.setUserId(7L);
        preference.setCourseId("os");
        preference.setKnowledgeBaseId(3L);
        preference.setIndexRunId(17L);
        preference.setEnabled(true);
        return preference;
    }

    private QaLearningMemories memory(Long id, String type, String text, LocalDateTime updatedAt) {
        QaLearningMemories memory = new QaLearningMemories();
        memory.setId(id);
        memory.setUserId(7L);
        memory.setCourseId("os");
        memory.setKnowledgeBaseId(3L);
        memory.setIndexRunId(17L);
        memory.setMemoryType(type);
        memory.setMemoryText(text);
        memory.setStatus("active");
        memory.setCreatedAt(updatedAt);
        memory.setUpdatedAt(updatedAt);
        return memory;
    }
}
