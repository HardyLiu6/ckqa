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
    void shouldUseRecentForGlobalAndDriftFollowUp() {
        QaContextAssembler assembler = new QaContextAssembler();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "什么是死锁？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。")
        );

        QaContextAssembly global = assembler.assemble("global", "它和资源分配图有什么关系？", history);
        QaContextAssembly drift = assembler.assemble("drift", "它和资源分配图有什么关系？", history);

        assertThat(global.strategy()).isEqualTo("recent");
        assertThat(global.contextApplied()).isTrue();
        assertThat(global.snapshotText()).contains("学生：什么是死锁？", "助手：死锁是多个进程互相等待资源的状态。");
        assertThat(global.latestTopic()).isEqualTo("死锁");
        assertThat(drift.strategy()).isEqualTo("recent");
        assertThat(drift.contextApplied()).isTrue();
        assertThat(drift.latestTopic()).isEqualTo("死锁");
    }

    @Test
    void shouldUseRecentForHybridFollowUp() {
        QaContextAssembler assembler = new QaContextAssembler();

        QaContextAssembly assembly = assembler.assemble("hybrid_v0", "它和资源分配图有什么关系？", List.of(
                message(1L, "user", 1, "什么是死锁？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。")
        ));

        assertThat(assembly.strategy()).isEqualTo("recent");
        assertThat(assembly.contextApplied()).isTrue();
        assertThat(assembly.latestTopic()).isEqualTo("死锁");
    }

    @Test
    void shouldKeepLatestNonPronounTopicAcrossRepeatedPronounTurns() {
        QaContextAssembler assembler = new QaContextAssembler();

        QaContextAssembly assembly = assembler.assemble("local", "它如何预防？", List.of(
                message(1L, "user", 1, "什么是死锁？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。"),
                message(3L, "user", 3, "它有什么特点？"),
                message(4L, "assistant", 4, "死锁通常具有互斥、请求保持、不可剥夺和循环等待。"),
                message(5L, "user", 5, "它怎么检测？"),
                message(6L, "assistant", 6, "可以通过资源分配图或等待图检测。")
        ));

        assertThat(assembly.strategy()).isEqualTo("recent");
        assertThat(assembly.latestTopic()).isEqualTo("死锁");
        assertThat(assembly.latestTopicMessageRange()).isEqualTo("1-2");
    }

    @Test
    void shouldResolveFormerAndLatterFromComparisonTopicStack() {
        QaContextAssembler assembler = new QaContextAssembler();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "死锁和饥饿有什么区别？"),
                message(2L, "assistant", 2, "死锁与饥饿都和资源等待有关，但触发条件不同。")
        );

        QaContextAssembly former = assembler.assemble("local", "前者如何检测？", history);
        QaContextAssembly latter = assembler.assemble("local", "后者如何避免？", history);

        assertThat(former.latestTopic()).isEqualTo("死锁");
        assertThat(former.topicSource()).isEqualTo("comparison_pronoun");
        assertThat(former.topicConfidence()).isGreaterThanOrEqualTo(0.8);
        assertThat(former.topicStackJson()).contains("死锁", "饥饿");
        assertThat(latter.latestTopic()).isEqualTo("饥饿");
        assertThat(latter.topicSource()).isEqualTo("comparison_pronoun");
    }

    @Test
    void shouldRestoreTopicFromStructuredSummaryForSummaryOnlyPronounFollowUp() {
        QaContextAssembler assembler = new QaContextAssembler();

        QaContextAssembly assembly = assembler.assemble(
                "basic",
                "它怎么判断？",
                List.of(),
                new QaContextSummary("本会话已讨论死锁定义。", 12, "死锁", "1-2", "[{\"topic\":\"死锁\"}]")
        );

        assertThat(assembly.strategy()).isEqualTo("summary");
        assertThat(assembly.latestTopic()).isEqualTo("死锁");
        assertThat(assembly.latestTopicMessageRange()).isEqualTo("1-2");
        assertThat(assembly.topicSource()).isEqualTo("summary");
        assertThat(assembly.topicStackJson()).contains("死锁");
    }

    @Test
    void shouldUseSummaryRecentWhenActiveSummaryExistsForBasicFollowUp() {
        QaContextAssembler assembler = new QaContextAssembler();

        QaContextAssembly assembly = assembler.assemble(
                "basic",
                "它和资源分配图有什么关系？",
                List.of(
                        message(1L, "user", 1, "什么是死锁？"),
                        message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。"),
                        message(3L, "user", 3, "那银行家算法呢？"),
                        message(4L, "assistant", 4, "银行家算法通过安全性检查避免进入不安全状态。")
                ),
                new QaContextSummary("本会话已讨论死锁定义。", 2)
        );

        assertThat(assembly.strategy()).isEqualTo("summary_recent");
        assertThat(assembly.snapshotText()).contains("会话摘要：", "本会话已讨论死锁定义。", "最近对话：");
        assertThat(assembly.snapshotText()).doesNotContain("学生：什么是死锁？");
        assertThat(assembly.snapshotText()).contains("学生：那银行家算法呢？");
        assertThat(assembly.messageRange()).isEqualTo("3-4");
        assertThat(assembly.charCount()).isGreaterThan(0);
    }

    @Test
    void shouldUseSummaryRecentForGlobalAndDriftWhenSummaryExists() {
        QaContextAssembler assembler = new QaContextAssembler();
        List<QaMessages> history = List.of(
                message(1L, "user", 1, "什么是死锁？"),
                message(2L, "assistant", 2, "死锁是多个进程互相等待资源的状态。"),
                message(3L, "user", 3, "它和资源分配图有什么关系？"),
                message(4L, "assistant", 4, "资源分配图可以帮助观察环路。")
        );

        QaContextSummary summary = new QaContextSummary("本会话已讨论死锁定义。", 2);

        QaContextAssembly global = assembler.assemble("global", "总结一下", history, summary);
        QaContextAssembly drift = assembler.assemble("drift", "扩展关联", history, summary);

        assertThat(global.strategy()).isEqualTo("summary_recent");
        assertThat(global.snapshotText()).contains("会话摘要：", "本会话已讨论死锁定义。", "最近对话：");
        assertThat(global.snapshotText()).doesNotContain("学生：什么是死锁？");
        assertThat(global.snapshotText()).contains("学生：它和资源分配图有什么关系？");
        assertThat(drift.strategy()).isEqualTo("summary_recent");
        assertThat(drift.snapshotText()).contains("助手：资源分配图可以帮助观察环路。");
        assertThat(global.latestTopic()).isEqualTo("死锁");
        assertThat(drift.latestTopic()).isEqualTo("死锁");
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
