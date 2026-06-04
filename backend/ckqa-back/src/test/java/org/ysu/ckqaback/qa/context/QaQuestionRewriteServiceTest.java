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
        assertThat(result.standaloneQueryText()).isEqualTo("关于上一轮主题「死锁」：它和资源分配图有什么关系？");
        assertThat(result.rewriteApplied()).isTrue();
        assertThat(result.rewriteReason()).contains("明显指代");
        assertThat(result.rewriteSourceMessageRange()).isEqualTo("1-2");
        assertThat(result.rewriteMethod()).isEqualTo("rule");
    }

    @Test
    void shouldRewriteThatOnePronounFollowUpWithRecentTopic() {
        QaContextAssembly context = new QaContextAssembly("recent", "最近对话", "1-2", 20, "死锁", "1-2");

        assertThat(QaContextPolicy.isPronounFollowUp("那个有哪些典型场景？")).isTrue();
        assertThat(QaContextPolicy.isPronounFollowUp("那一个有哪些典型场景？")).isTrue();
        QaQuestionRewriteResult result = rewriteService.rewrite("basic", "那个有哪些典型场景？", context);

        assertThat(result.retrievalQueryText()).isEqualTo("关于上一轮主题「死锁」：那个有哪些典型场景？");
        assertThat(result.rewriteApplied()).isTrue();
        assertThat(result.rewriteMethod()).isEqualTo("rule");
    }

    @Test
    void shouldRewritePronounDefinitionQuestionWithRecentTopic() {
        QaContextAssembly context = new QaContextAssembly("recent", "最近对话", "1-2", 20, "死锁", "1-2");

        QaQuestionRewriteResult result = rewriteService.rewrite("basic", "它的定义是什么？", context);

        assertThat(result.retrievalQueryText()).isEqualTo("关于上一轮主题「死锁」：它的定义是什么？");
        assertThat(result.rewriteApplied()).isTrue();
        assertThat(result.rewriteMethod()).isEqualTo("rule");
    }

    @Test
    void shouldUseHighConfidenceLlmStandaloneQuery() {
        QaQuestionRewriteService service = new QaQuestionRewriteService();
        service.setLlmClient((question, context) -> QaLlmQuestionRewriteResult.success(
                "死锁和资源分配图有什么关系？",
                0.91D,
                "消解它指代死锁",
                "deepseek-v4-flash"
        ));
        QaContextAssembly context = new QaContextAssembly(
                "recent",
                "学生：什么是死锁？\n助手：死锁是多个进程互相等待资源的状态。",
                "1-2",
                40,
                "死锁",
                "1-2"
        );

        QaQuestionRewriteResult result = service.rewrite("basic", "它和资源分配图有什么关系？", context);

        assertThat(result.retrievalQueryText()).isEqualTo("死锁和资源分配图有什么关系？");
        assertThat(result.standaloneQueryText()).isEqualTo("死锁和资源分配图有什么关系？");
        assertThat(result.rewriteMethod()).isEqualTo("llm");
        assertThat(result.rewriteModel()).isEqualTo("deepseek-v4-flash");
        assertThat(result.rewriteConfidence()).isEqualTo(0.91D);
    }

    @Test
    void shouldFallbackToRuleWhenLlmConfidenceIsLow() {
        QaQuestionRewriteService service = new QaQuestionRewriteService();
        service.setLlmClient((question, context) -> QaLlmQuestionRewriteResult.success(
                "它和资源分配图有什么关系？",
                0.2D,
                "不确定",
                "deepseek-v4-flash"
        ));
        QaContextAssembly context = new QaContextAssembly("recent", "最近对话", "1-2", 20, "死锁", "1-2");

        QaQuestionRewriteResult result = service.rewrite("basic", "它和资源分配图有什么关系？", context);

        assertThat(result.retrievalQueryText()).isEqualTo("关于上一轮主题「死锁」：它和资源分配图有什么关系？");
        assertThat(result.rewriteMethod()).isEqualTo("rule");
    }

    @Test
    void shouldNotRewriteCompleteQuestion() {
        QaContextAssembly context = new QaContextAssembly("recent", "", "1-2", 0, "死锁", "1-2");

        QaQuestionRewriteResult result = rewriteService.rewrite("basic", "死锁和资源分配图有什么关系？", context);

        assertThat(result.retrievalQueryText()).isEqualTo("死锁和资源分配图有什么关系？");
        assertThat(result.rewriteApplied()).isFalse();
        assertThat(result.rewriteReason()).contains("已经包含上一轮主题");
        assertThat(result.rewriteMethod()).isEqualTo("none");
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
    void shouldRewriteGlobalAndDriftFollowUpWithRecentTopic() {
        QaContextAssembly context = new QaContextAssembly("recent", "", "1-2", 0, "死锁", "1-2");

        QaQuestionRewriteResult global = rewriteService.rewrite("global", "它是什么意思？", context);
        QaQuestionRewriteResult drift = rewriteService.rewrite("drift", "它是什么意思？", context);

        assertThat(global.retrievalQueryText()).isEqualTo("关于上一轮主题「死锁」：它是什么意思？");
        assertThat(global.rewriteApplied()).isTrue();
        assertThat(global.rewriteMethod()).isEqualTo("rule");
        assertThat(drift.retrievalQueryText()).isEqualTo("关于上一轮主题「死锁」：它是什么意思？");
        assertThat(drift.rewriteApplied()).isTrue();
        assertThat(drift.rewriteMethod()).isEqualTo("rule");
    }

    @Test
    void shouldRewriteHybridFollowUpWithRecentTopic() {
        QaContextAssembly context = new QaContextAssembly("recent", "最近对话", "1-2", 20, "死锁", "1-2");

        QaQuestionRewriteResult result = rewriteService.rewrite("hybrid_v0", "它和资源分配图有什么关系？", context);

        assertThat(result.retrievalQueryText()).isEqualTo("关于上一轮主题「死锁」：它和资源分配图有什么关系？");
        assertThat(result.rewriteApplied()).isTrue();
        assertThat(result.rewriteMethod()).isEqualTo("rule");
    }

    @Test
    void shouldRewriteComparisonPronounWithResolvedTopicAnchor() {
        QaContextAssembly context = new QaContextAssembly(
                "recent",
                "学生：银行家算法和资源分配图有什么区别？",
                "3-4",
                24,
                "银行家算法",
                "3-4",
                "comparison_pronoun",
                0.86D,
                "[{\"topic\":\"银行家算法\",\"role\":\"former\"},{\"topic\":\"资源分配图\",\"role\":\"latter\"}]"
        );

        QaQuestionRewriteResult result = rewriteService.rewrite("global", "前者如何检测？", context);

        assertThat(result.retrievalQueryText()).isEqualTo("关于上一轮主题「银行家算法」：前者如何检测？");
        assertThat(result.rewriteApplied()).isTrue();
        assertThat(result.rewriteMethod()).isEqualTo("rule");
        assertThat(result.rewriteSourceMessageRange()).isEqualTo("3-4");
    }
}
