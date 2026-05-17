package org.ysu.ckqaback.qa.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QaQuestionRewriteServiceTest {

    private final QaQuestionRewriteService rewriteService = new QaQuestionRewriteService();

    @Test
    void shouldRewriteObviousPronounFollowUpWithRecentTopic() {
        QaContextAssembly context = new QaContextAssembly(
                "recent",
                "学生：什么是死锁？\n助手：死锁是多个进程互相等待资源的状态。",
                "1-2",
                40,
                "死锁",
                "1-2"
        );

        QaQuestionRewriteResult result = rewriteService.rewrite("basic", "它和资源分配图有什么关系？", context);

        assertThat(result.retrievalQueryText()).isEqualTo("关于上一轮主题「死锁」：它和资源分配图有什么关系？");
        assertThat(result.rewriteApplied()).isTrue();
        assertThat(result.rewriteReason()).contains("明显指代");
        assertThat(result.rewriteSourceMessageRange()).isEqualTo("1-2");
    }

    @Test
    void shouldNotRewriteCompleteQuestion() {
        QaContextAssembly context = new QaContextAssembly("recent", "", "1-2", 0, "死锁", "1-2");

        QaQuestionRewriteResult result = rewriteService.rewrite("basic", "死锁和资源分配图有什么关系？", context);

        assertThat(result.retrievalQueryText()).isEqualTo("死锁和资源分配图有什么关系？");
        assertThat(result.rewriteApplied()).isFalse();
        assertThat(result.rewriteReason()).contains("已经包含上一轮主题");
    }

    @Test
    void shouldNotRewriteWithoutRecentTopic() {
        QaContextAssembly context = new QaContextAssembly("none", "", "", 0, "", "");

        QaQuestionRewriteResult result = rewriteService.rewrite("basic", "它是什么意思？", context);

        assertThat(result.retrievalQueryText()).isEqualTo("它是什么意思？");
        assertThat(result.rewriteApplied()).isFalse();
        assertThat(result.rewriteReason()).contains("没有可用上文主题");
    }

    @Test
    void shouldNotRewriteGlobalOrDrift() {
        QaContextAssembly context = new QaContextAssembly("recent", "", "1-2", 0, "死锁", "1-2");

        assertThat(rewriteService.rewrite("global", "它是什么意思？", context).rewriteApplied()).isFalse();
        assertThat(rewriteService.rewrite("drift", "它是什么意思？", context).rewriteApplied()).isFalse();
    }
}
