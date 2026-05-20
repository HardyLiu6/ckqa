package org.ysu.ckqaback.qa.memory;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaLearningMemories;

import static org.assertj.core.api.Assertions.assertThat;

class QaMemoryInjectionRouterTest {

    private final QaMemoryInjectionRouter router = new QaMemoryInjectionRouter();

    @Test
    void pronounFollowUpShouldOnlyAllowPreferences() {
        QaMemoryInjectionDecision decision = router.decide("它的实际作用是什么？", "线程");

        assertThat(decision.longMemoryMode()).isEqualTo("preference_only");
        assertThat(decision.allowLearningTopic()).isFalse();
        assertThat(decision.allowPreference()).isTrue();
        assertThat(decision.allowUnresolvedFocus()).isFalse();
        assertThat(router.shouldInclude(decision, memory("learning_topic", "学生关注：进程"), "它的实际作用是什么？", "线程"))
                .isFalse();
        assertThat(router.shouldInclude(decision, memory("explanation_preference", "偏好步骤化解释"), "它的实际作用是什么？", "线程"))
                .isTrue();
    }

    @Test
    void independentDefinitionShouldNotInjectHistoricalTopics() {
        QaMemoryInjectionDecision decision = router.decide("什么是线程？", "");

        assertThat(decision.longMemoryMode()).isEqualTo("preference_only");
        assertThat(decision.allowLearningTopic()).isFalse();
        assertThat(decision.allowPreference()).isTrue();
        assertThat(router.shouldInclude(decision, memory("learning_topic", "学生关注：进程"), "什么是线程？", ""))
                .isFalse();
    }

    @Test
    void explicitContinuationShouldAllowRelevantLearningFocus() {
        QaMemoryInjectionDecision decision = router.decide("继续讲我之前没弄懂的部分", "");

        assertThat(decision.longMemoryMode()).isEqualTo("relevant_memory");
        assertThat(decision.allowLearningTopic()).isTrue();
        assertThat(decision.allowPreference()).isTrue();
        assertThat(decision.allowUnresolvedFocus()).isTrue();
        assertThat(router.shouldInclude(decision, memory("learning_topic", "学生关注：进程"), "继续讲我之前没弄懂的部分", ""))
                .isTrue();
        assertThat(router.shouldInclude(decision, memory("unresolved_focus", "持续关注概念关系与对比"), "继续讲我之前没弄懂的部分", ""))
                .isTrue();
    }

    @Test
    void currentTopicShouldFilterObviouslyDifferentLearningTopic() {
        QaMemoryInjectionDecision decision = router.decide("继续讲它的实际作用", "线程");

        assertThat(decision.longMemoryMode()).isEqualTo("preference_only");
        assertThat(router.shouldInclude(decision, memory("learning_topic", "学生关注：进程"), "继续讲它的实际作用", "线程"))
                .isFalse();
        assertThat(router.shouldInclude(decision, memory("explanation_preference", "偏好结合例子解释"), "继续讲它的实际作用", "线程"))
                .isTrue();
    }

    private QaLearningMemories memory(String type, String text) {
        QaLearningMemories memory = new QaLearningMemories();
        memory.setMemoryType(type);
        memory.setMemoryText(text);
        memory.setStatus("active");
        return memory;
    }
}
