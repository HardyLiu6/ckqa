package org.ysu.ckqaback.qa.memory;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaLearningMemories;
import org.ysu.ckqaback.entity.QaMemoryPreferences;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.integration.graphrag.GraphRagConversationMessage;
import org.ysu.ckqaback.service.QaLearningMemoriesService;
import org.ysu.ckqaback.service.QaMemoryPreferencesService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class QaMemoryContextServiceTest {

    @Test
    void shouldNotApplyWhenDefaultPreferenceIsMissing() {
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryContextService service = new QaMemoryContextService(preferencesService, memoriesService);
        QaSessions session = session();

        QaMemoryContextResult result = service.buildContext("local", "default", session, List.of());

        assertThat(result.memoryApplied()).isFalse();
        assertThat(result.strategy()).isEqualTo("none");
        assertThat(result.conversationHistory()).isEmpty();
    }

    @Test
    void shouldNeverApplyWhenPolicyIsOff() {
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryContextService service = new QaMemoryContextService(preferencesService, memoriesService);

        QaMemoryContextResult result = service.buildContext("local", "off", session(), List.of(message(1L, "user", 1, "什么是死锁？")));

        assertThat(result.memoryApplied()).isFalse();
        assertThat(result.historyFallbackReason()).isEqualTo("policy_off");
    }

    @Test
    void shouldApplyAutoOnlyWhenPreferenceIsEnabled() {
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryContextService service = new QaMemoryContextService(preferencesService, memoriesService);
        QaSessions session = session();
        given(preferencesService.findByScope(7L, "os", 3L, 17L)).willReturn(enabledPreference());
        given(memoriesService.listActiveByScope(7L, "os", 3L, 17L, 3)).willReturn(List.of(memory(101L, "偏好用步骤化解释调度算法。")));

        QaMemoryContextResult result = service.buildContext("local", "auto", session, List.of(
                message(1L, "user", 1, "什么是时间片轮转？"),
                message(2L, "assistant", 2, "时间片轮转是一种抢占式调度。")
        ));

        assertThat(result.memoryApplied()).isTrue();
        assertThat(result.strategy()).isEqualTo("local_history");
        assertThat(result.scope()).isEqualTo("userId=7;courseId=os;knowledgeBaseId=3;indexRunId=17");
        assertThat(result.sourceCount()).isEqualTo(3);
        assertThat(result.conversationHistory()).extracting(GraphRagConversationMessage::content)
                .contains("学习记忆：偏好用步骤化解释调度算法。");
    }

    @Test
    void shouldIsolateBySessionIndexRunId() {
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryContextService service = new QaMemoryContextService(preferencesService, memoriesService);
        QaSessions session = session();
        session.setIndexRunId(99L);
        given(preferencesService.findByScope(7L, "os", 3L, 99L)).willReturn(enabledPreference(99L));

        QaMemoryContextResult result = service.buildContext("local", "default", session, List.of(message(1L, "user", 1, "问题")));

        assertThat(result.memoryApplied()).isTrue();
        assertThat(result.scope()).contains("indexRunId=99");
    }

    @Test
    void shouldTrimMessagesAndMemoriesWithinBudget() {
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryContextService service = new QaMemoryContextService(preferencesService, memoriesService);
        QaSessions session = session();
        given(preferencesService.findByScope(7L, "os", 3L, 17L)).willReturn(enabledPreference());
        given(memoriesService.listActiveByScope(7L, "os", 3L, 17L, 3)).willReturn(List.of(
                memory(101L, "A".repeat(700)),
                memory(102L, "B".repeat(700)),
                memory(103L, "C".repeat(100))
        ));

        QaMemoryContextResult result = service.buildContext("local", "default", session, List.of(
                message(1L, "user", 1, "u1"),
                message(2L, "assistant", 2, "a2"),
                message(3L, "user", 3, "u3"),
                message(4L, "assistant", 4, "a4"),
                message(5L, "user", 5, "u5"),
                message(6L, "assistant", 6, "a6"),
                message(7L, "user", 7, "u7")
        ));

        assertThat(result.conversationHistory()).hasSizeLessThanOrEqualTo(9);
        assertThat(result.sizeEstimate()).isLessThanOrEqualTo(3000);
        assertThat(result.conversationHistory().stream().filter(item -> item.content().startsWith("学习记忆：")).count())
                .isLessThanOrEqualTo(2);
    }

    @Test
    void shouldNotApplyForNonLocalMode() {
        QaMemoryPreferencesService preferencesService = mock(QaMemoryPreferencesService.class);
        QaLearningMemoriesService memoriesService = mock(QaLearningMemoriesService.class);
        QaMemoryContextService service = new QaMemoryContextService(preferencesService, memoriesService);

        QaMemoryContextResult result = service.buildContext("basic", "auto", session(), List.of(message(1L, "user", 1, "问题")));

        assertThat(result.memoryApplied()).isFalse();
        assertThat(result.historyFallbackReason()).isEqualTo("mode_not_local");
    }

    private QaSessions session() {
        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setUserId(7L);
        session.setCourseId("os");
        session.setKnowledgeBaseId(3L);
        session.setIndexRunId(17L);
        return session;
    }

    private QaMessages message(Long id, String role, int sequenceNo, String content) {
        QaMessages message = new QaMessages();
        message.setId(id);
        message.setRole(role);
        message.setSequenceNo(sequenceNo);
        message.setContent(content);
        return message;
    }

    private QaMemoryPreferences enabledPreference() {
        return enabledPreference(17L);
    }

    private QaMemoryPreferences enabledPreference(Long indexRunId) {
        QaMemoryPreferences preference = new QaMemoryPreferences();
        preference.setUserId(7L);
        preference.setCourseId("os");
        preference.setKnowledgeBaseId(3L);
        preference.setIndexRunId(indexRunId);
        preference.setEnabled(true);
        return preference;
    }

    private QaLearningMemories memory(Long id, String text) {
        QaLearningMemories memory = new QaLearningMemories();
        memory.setId(id);
        memory.setMemoryText(text);
        memory.setStatus("active");
        return memory;
    }
}
