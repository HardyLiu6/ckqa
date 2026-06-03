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
