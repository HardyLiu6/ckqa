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
