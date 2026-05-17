package org.ysu.ckqaback.qa.context;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaMessages;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QaContextAssemblerTest {

    @Test
    void shouldUseNoneForFirstTurn() {
        QaContextAssembler assembler = new QaContextAssembler();

        QaContextAssembly assembly = assembler.assemble("basic", "什么是死锁？", List.of());

        assertThat(assembly.strategy()).isEqualTo("none");
        assertThat(assembly.contextApplied()).isFalse();
        assertThat(assembly.snapshotText()).isEmpty();
        assertThat(assembly.charCount()).isZero();
    }

    @Test
    void shouldUseRecentForBasicFollowUpAndKeepLatestTopic() {
        QaContextAssembler assembler = new QaContextAssembler();

        QaContextAssembly assembly = assembler.assemble("basic", "它和资源分配图有什么关系？", List.of(
                message(1L, "user", 1, "什么是死锁？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。")
        ));

        assertThat(assembly.strategy()).isEqualTo("recent");
        assertThat(assembly.contextApplied()).isTrue();
        assertThat(assembly.snapshotText()).contains("学生：什么是死锁？", "助手：死锁是多个进程互相等待资源的状态。");
        assertThat(assembly.messageRange()).isEqualTo("1-2");
        assertThat(assembly.latestTopic()).isEqualTo("死锁");
        assertThat(assembly.latestTopicMessageRange()).isEqualTo("1-2");
        assertThat(assembly.charCount()).isGreaterThan(0);
    }

    @Test
    void shouldLimitRecentMessagesAndCharacterBudget() {
        QaContextAssembler assembler = new QaContextAssembler();
        List<QaMessages> messages = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            messages.add(message((long) index, index % 2 == 0 ? "assistant" : "user", index, "消息".repeat(220) + index));
        }

        QaContextAssembly assembly = assembler.assemble("local", "这个概念怎么理解？", messages);

        assertThat(assembly.strategy()).isEqualTo("recent");
        assertThat(assembly.snapshotText()).doesNotContain("消息".repeat(220) + "1");
        assertThat(assembly.charCount()).isLessThanOrEqualTo(1800);
        assertThat(assembly.snapshotText().length()).isLessThanOrEqualTo(3500);
    }

    @Test
    void shouldNotApplyHistoryForGlobalAndDrift() {
        QaContextAssembler assembler = new QaContextAssembler();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "什么是死锁？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。")
        );

        assertThat(assembler.assemble("global", "总结课程", history).strategy()).isEqualTo("none");
        assertThat(assembler.assemble("drift", "关联一下", history).strategy()).isEqualTo("none");
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
