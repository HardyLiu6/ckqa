package org.ysu.ckqaback.qa.context;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaMessages;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QaTopicResolverTest {

    @Test
    void shouldExtractExplicitTopicFromWhatIsQuestion() {
        QaTopicStack stack = new QaTopicResolver().resolve("死锁是什么？", List.of(), null);

        assertThat(stack.latestTopic()).isEqualTo("死锁");
        assertThat(stack.topicSource()).isEqualTo("explicit");
        assertThat(stack.topicConfidence()).isGreaterThanOrEqualTo(0.9);

        assertThat(new QaTopicResolver().resolve("什么是死锁？", List.of(), null).latestTopic()).isEqualTo("死锁");
        assertThat(new QaTopicResolver().resolve("请解释死锁", List.of(), null).latestTopic()).isEqualTo("死锁");
    }

    @Test
    void shouldExtractTopicFromThenTopicFollowUp() {
        QaTopicStack stack = new QaTopicResolver().resolve(
                "那 饥饿 呢？",
                List.of(
                        message(1L, "user", 1, "什么是死锁？"),
                        message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。")
                ),
                null
        );

        assertThat(stack.latestTopic()).isEqualTo("饥饿");
        assertThat(stack.topicSource()).isEqualTo("explicit_follow_up");
        assertThat(stack.activeTopicsJson()).contains("死锁", "饥饿");
    }

    @Test
    void shouldResolvePronounDefinitionQuestionToPreviousStableTopic() {
        QaTopicResolver resolver = new QaTopicResolver();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "死锁是什么？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。")
        );

        QaTopicStack stack = resolver.resolve("它的定义是什么？", history, null);

        assertThat(stack.latestTopic()).isEqualTo("死锁");
        assertThat(stack.topicSource()).isEqualTo("history");
        assertThat(stack.latestTopicMessageRange()).isEqualTo("1-2");
        assertThat(stack.activeTopicsJson()).contains("死锁").doesNotContain("它的定义");
    }

    @Test
    void shouldBindFormerAndLatterToMostRecentComparisonPair() {
        QaTopicResolver resolver = new QaTopicResolver();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "什么是死锁？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。"),
                message(3L, "user", 3, "银行家算法和资源分配图有什么区别？"),
                message(4L, "assistant", 4, "银行家算法通过安全性检查避免进入不安全状态，资源分配图用于观察资源请求关系。")
        );

        QaTopicStack former = resolver.resolve("前者如何检测？", history, null);
        QaTopicStack latter = resolver.resolve("后者如何使用？", history, null);

        assertThat(former.latestTopic()).isEqualTo("银行家算法");
        assertThat(former.topicSource()).isEqualTo("comparison_pronoun");
        assertThat(latter.latestTopic()).isEqualTo("资源分配图");
        assertThat(latter.topicSource()).isEqualTo("comparison_pronoun");
    }

    @Test
    void shouldKeepComparisonPairAfterFormerPronounTurn() {
        QaTopicResolver resolver = new QaTopicResolver();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "死锁和饥饿有什么区别？"),
                message(2L, "assistant", 2, "死锁是互相等待形成的循环僵局，饥饿是进程长期得不到资源。"),
                message(3L, "user", 3, "前者如何检测？"),
                message(4L, "assistant", 4, "死锁可以通过资源分配图或等待图检测。")
        );

        QaTopicStack stack = resolver.resolve("后者如何避免？", history, null);

        assertThat(stack.latestTopic()).isEqualTo("饥饿");
        assertThat(stack.topicSource()).isEqualTo("comparison_pronoun");
        assertThat(stack.activeTopicsJson()).contains("死锁", "饥饿");
        assertThat(stack.activeTopicsJson()).contains(
                "{\"topic\":\"死锁\",\"role\":\"former\"}",
                "{\"topic\":\"饥饿\",\"role\":\"latter\"}"
        );
    }

    @Test
    void shouldStripDeSuffixFromLatterComparisonTopic() {
        QaTopicResolver resolver = new QaTopicResolver();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "死锁和饥饿的区别是什么？"),
                message(2L, "assistant", 2, "死锁和饥饿都和资源等待有关，但表现不同。")
        );

        QaTopicStack former = resolver.resolve("前者如何检测？", history, null);
        QaTopicStack latter = resolver.resolve("后者如何避免？", history, null);

        assertThat(former.latestTopic()).isEqualTo("死锁");
        assertThat(latter.latestTopic()).isEqualTo("饥饿");
        assertThat(latter.topicSource()).isEqualTo("comparison_pronoun");
        assertThat(latter.activeTopicsJson()).contains("死锁", "饥饿");
        assertThat(latter.activeTopicsJson()).doesNotContain("饥饿的");
    }

    @Test
    void shouldStripBetweenSuffixFromLatterComparisonTopic() {
        QaTopicResolver resolver = new QaTopicResolver();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "死锁与饥饿之间有什么区别？"),
                message(2L, "assistant", 2, "死锁与饥饿都和资源等待有关，但触发条件不同。")
        );

        QaTopicStack former = resolver.resolve("前者如何检测？", history, null);
        QaTopicStack latter = resolver.resolve("后者如何避免？", history, null);

        assertThat(former.latestTopic()).isEqualTo("死锁");
        assertThat(latter.latestTopic()).isEqualTo("饥饿");
        assertThat(latter.topicSource()).isEqualTo("comparison_pronoun");
        assertThat(latter.activeTopicsJson()).contains("死锁", "饥饿");
        assertThat(latter.activeTopicsJson()).doesNotContain("饥饿之间");
    }

    @Test
    void shouldPreserveRealTopicSuffixWhenResolvingComparisonPronouns() {
        QaTopicResolver resolver = new QaTopicResolver();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "时间与空间有什么区别？"),
                message(2L, "assistant", 2, "时间描述变化的顺序，空间描述对象的位置与范围。")
        );

        QaTopicStack former = resolver.resolve("前者如何理解？", history, null);
        QaTopicStack latter = resolver.resolve("后者如何理解？", history, null);

        assertThat(former.latestTopic()).isEqualTo("时间");
        assertThat(latter.latestTopic()).isEqualTo("空间");
        assertThat(latter.activeTopicsJson())
                .contains("{\"topic\":\"时间\",\"role\":\"former\"}", "{\"topic\":\"空间\",\"role\":\"latter\"}")
                .doesNotContain("\"topic\":\"时\"", "\"topic\":\"空\"");
    }

    @Test
    void shouldNotGuessFormerOrLatterWithoutValidComparisonPair() {
        QaTopicResolver resolver = new QaTopicResolver();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "什么是死锁？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。"),
                message(3L, "user", 3, "那资源分配图呢？"),
                message(4L, "assistant", 4, "资源分配图用于表示资源请求关系。")
        );

        QaTopicStack former = resolver.resolve("前者如何检测？", history, null);
        QaTopicStack latter = resolver.resolve("后者如何使用？", history, null);

        assertThat(former.latestTopic()).isEmpty();
        assertThat(former.topicSource()).isEmpty();
        assertThat(latter.latestTopic()).isEmpty();
        assertThat(latter.topicSource()).isEmpty();
    }

    @Test
    void shouldRestoreMostRecentComparisonPairFromSummary() {
        QaContextSummary summary = new QaContextSummary(
                "本会话先讨论死锁，随后比较银行家算法和资源分配图。",
                12,
                "资源分配图",
                "9-10",
                "[{\"topic\":\"死锁\"},{\"topic\":\"银行家算法\",\"role\":\"former\"},{\"topic\":\"资源分配图\",\"role\":\"latter\"}]"
        );
        QaTopicResolver resolver = new QaTopicResolver();

        QaTopicStack former = resolver.resolve("前者如何检测？", List.of(), summary);
        QaTopicStack latter = resolver.resolve("后者如何使用？", List.of(), summary);

        assertThat(former.latestTopic()).isEqualTo("银行家算法");
        assertThat(former.topicSource()).isEqualTo("comparison_pronoun");
        assertThat(latter.latestTopic()).isEqualTo("资源分配图");
        assertThat(latter.topicSource()).isEqualTo("comparison_pronoun");
    }

    @Test
    void shouldRestoreLatestTopicFromLegacySummaryTopicsJson() {
        QaContextSummary summary = new QaContextSummary(
                "旧摘要只保存主题列表。",
                8,
                "",
                "",
                "[{\"topic\":\"死锁\"}]"
        );

        QaTopicStack stack = new QaTopicResolver().resolve("", List.of(), summary);

        assertThat(stack.latestTopic()).isEqualTo("死锁");
        assertThat(stack.topicSource()).isEqualTo("summary");
    }

    @Test
    void shouldRestoreTopicAndComparisonPairFromSemanticStateJsonOnly() {
        String semanticStateJson = """
                {"version":"session_semantic_state_v1","latestTopic":"资源分配图","latestTopicMessageRange":"9-10","topicSource":"history","topicConfidence":0.82,"activeTopics":[{"topic":"死锁"},{"topic":"银行家算法","role":"former"},{"topic":"资源分配图","role":"latter"}],"comparisonTopics":[{"topic":"银行家算法","role":"former"},{"topic":"资源分配图","role":"latter"}],"restoredFromSummary":true,"summaryUntilSequenceNo":12}
                """;
        QaContextSummary summary = new QaContextSummary(
                "摘要字段迁移中只保留了结构化语义状态。",
                12,
                "",
                "",
                "",
                SessionSemanticState.VERSION,
                semanticStateJson
        );
        QaTopicResolver resolver = new QaTopicResolver();

        QaTopicStack pronoun = resolver.resolve("它怎么用？", List.of(), summary);
        QaTopicStack former = resolver.resolve("前者如何检测？", List.of(), summary);
        QaTopicStack latter = resolver.resolve("后者如何使用？", List.of(), summary);

        assertThat(pronoun.latestTopic()).isEqualTo("资源分配图");
        assertThat(pronoun.latestTopicMessageRange()).isEqualTo("9-10");
        assertThat(pronoun.topicSource()).isEqualTo("summary");
        assertThat(pronoun.activeTopicsJson()).contains("死锁", "银行家算法", "资源分配图");
        assertThat(former.latestTopic()).isEqualTo("银行家算法");
        assertThat(former.topicSource()).isEqualTo("comparison_pronoun");
        assertThat(latter.latestTopic()).isEqualTo("资源分配图");
        assertThat(latter.topicSource()).isEqualTo("comparison_pronoun");
    }

    @Test
    void shouldIgnoreOutOfOrderHistoryAndKeepSequenceLatestTopic() {
        QaTopicResolver resolver = new QaTopicResolver();
        List<QaMessages> history = List.of(
                message(4L, "assistant", 4, "银行家算法通过安全性检查避免进入不安全状态。"),
                message(1L, "user", 1, "什么是死锁？"),
                message(3L, "user", 3, "那银行家算法呢？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。")
        );

        QaTopicStack stack = resolver.resolve("它有什么局限？", history, null);

        assertThat(stack.latestTopic()).isEqualTo("银行家算法");
        assertThat(stack.latestTopicMessageRange()).isEqualTo("3-4");
        assertThat(stack.topicSource()).isEqualTo("history");
        assertThat(stack.activeTopicsJson()).contains("死锁", "银行家算法");
    }

    @Test
    void shouldPreferCurrentExplicitTopicOverLongPronounHistory() {
        QaTopicResolver resolver = new QaTopicResolver();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "什么是死锁？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。"),
                message(3L, "user", 3, "它有什么特点？"),
                message(4L, "assistant", 4, "死锁有四个必要条件。"),
                message(5L, "user", 5, "这个怎么检测？"),
                message(6L, "assistant", 6, "可以通过等待图检测。"),
                message(7L, "user", 7, "上述方法有什么局限？"),
                message(8L, "assistant", 8, "检测本身不能主动解除死锁。")
        );

        QaTopicStack stack = resolver.resolve("银行家算法是什么？", history, null);

        assertThat(stack.latestTopic()).isEqualTo("银行家算法");
        assertThat(stack.topicSource()).isEqualTo("explicit");
        assertThat(stack.latestTopicMessageRange()).isEmpty();
        assertThat(stack.activeTopicsJson()).contains("死锁", "银行家算法");
    }

    @Test
    void shouldResetComparisonPronounsAfterLaterSingleTopic() {
        QaTopicResolver resolver = new QaTopicResolver();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "死锁和饥饿有什么区别？"),
                message(2L, "assistant", 2, "死锁与饥饿都和资源等待有关，但触发条件不同。"),
                message(3L, "user", 3, "那银行家算法呢？"),
                message(4L, "assistant", 4, "银行家算法通过安全性检查避免进入不安全状态。")
        );

        QaTopicStack former = resolver.resolve("前者如何检测？", history, null);
        QaTopicStack pronoun = resolver.resolve("它有什么局限？", history, null);

        assertThat(former.hasTopic()).isFalse();
        assertThat(former.topicSource()).isEmpty();
        assertThat(pronoun.latestTopic()).isEqualTo("银行家算法");
        assertThat(pronoun.topicSource()).isEqualTo("history");
    }

    private QaMessages message(Long id, String role, int sequenceNo, String content) {
        QaMessages message = new QaMessages();
        message.setId(id);
        message.setSessionId(5L);
        message.setRole(role);
        message.setSequenceNo(sequenceNo);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.of(2026, 5, 17, 12, sequenceNo));
        return message;
    }
}
