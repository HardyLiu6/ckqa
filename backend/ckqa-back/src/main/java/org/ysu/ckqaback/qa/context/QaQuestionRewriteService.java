package org.ysu.ckqaback.qa.context;

import org.springframework.util.StringUtils;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

import java.util.regex.Pattern;

/**
 * 追问改写服务：优先使用 LLM 生成独立检索问题，失败时回退规则式明显指代补全。
 */
public class QaQuestionRewriteService {

    private static final int MAX_RETRIEVAL_QUERY_CHARS = 800;
    private static final Pattern PRONOUN_PATTERN = Pattern.compile(".*(它|这个|这一个|该概念|上面那个|前者|后者|这种|上述).*");
    private QaQuestionRewriteClientPort llmClient;
    private CkqaIntegrationProperties.RewriteProperties rewriteProperties = new CkqaIntegrationProperties.RewriteProperties();

    public void setLlmClient(QaQuestionRewriteClientPort llmClient) {
        this.llmClient = llmClient;
    }

    public void setRewriteProperties(CkqaIntegrationProperties.RewriteProperties rewriteProperties) {
        if (rewriteProperties != null) {
            this.rewriteProperties = rewriteProperties;
        }
    }

    public QaQuestionRewriteResult rewrite(String mode, String originalQuestion, QaContextAssembly context) {
        String question = originalQuestion == null ? "" : originalQuestion.trim();
        if (!supportsRewrite(mode)) {
            return noRewrite(question, "当前模式不启用追问改写");
        }
        if (shouldTryLlmRewrite(question, context)) {
            QaQuestionRewriteResult llmResult = tryLlmRewrite(question, context);
            if (llmResult != null) {
                return llmResult;
            }
        }
        return ruleRewrite(question, context);
    }

    private QaQuestionRewriteResult ruleRewrite(String question, QaContextAssembly context) {
        if (context == null || !StringUtils.hasText(context.latestTopic())) {
            return noRewrite(question, "没有可用上文主题");
        }
        if (question.contains(context.latestTopic())) {
            return noRewrite(question, "当前问题已经包含上一轮主题");
        }
        if (!PRONOUN_PATTERN.matcher(question).matches()) {
            return noRewrite(question, "未命中明显指代词");
        }

        String rewritten = "关于上一轮主题「" + context.latestTopic() + "」：" + question;
        if (rewritten.length() > MAX_RETRIEVAL_QUERY_CHARS) {
            return noRewrite(question, "改写后超过检索问题长度限制");
        }
        return new QaQuestionRewriteResult(
                rewritten,
                rewritten,
                true,
                "命中明显指代词，并使用最近成功上文主题补全检索问题",
                context.latestTopicMessageRange(),
                "rule",
                "",
                null
        );
    }

    private QaQuestionRewriteResult noRewrite(String question, String reason) {
        return new QaQuestionRewriteResult(question, question, false, reason, "", "none", "", null);
    }

    private boolean shouldTryLlmRewrite(String question, QaContextAssembly context) {
        if (llmClient == null || rewriteProperties == null || !rewriteProperties.isEnabled()) {
            return false;
        }
        if (!StringUtils.hasText(question) || question.length() > maxChars()) {
            return false;
        }
        if (context == null || !context.contextApplied() || !StringUtils.hasText(context.latestTopic())) {
            return false;
        }
        if (question.contains(context.latestTopic())) {
            return false;
        }
        return PRONOUN_PATTERN.matcher(question).matches() || question.length() <= 40;
    }

    private QaQuestionRewriteResult tryLlmRewrite(String question, QaContextAssembly context) {
        QaLlmQuestionRewriteResult llmResult = llmClient.rewrite(question, context);
        if (llmResult == null || !llmResult.success()) {
            return null;
        }
        String standaloneQuery = llmResult.standaloneQueryText() == null ? "" : llmResult.standaloneQueryText().trim();
        if (!StringUtils.hasText(standaloneQuery)
                || standaloneQuery.length() > maxChars()
                || llmResult.confidence() < minConfidence()) {
            return null;
        }
        return new QaQuestionRewriteResult(
                standaloneQuery,
                standaloneQuery,
                true,
                StringUtils.hasText(llmResult.reason()) ? llmResult.reason() : "LLM 将追问改写为独立检索问题",
                context.latestTopicMessageRange(),
                "llm",
                llmResult.model(),
                llmResult.confidence()
        );
    }

    private int maxChars() {
        int configured = rewriteProperties == null ? 0 : rewriteProperties.getMaxChars();
        return configured > 0 ? configured : MAX_RETRIEVAL_QUERY_CHARS;
    }

    private boolean supportsRewrite(String mode) {
        return "basic".equals(mode) || "local".equals(mode) || "hybrid_v0".equals(mode);
    }

    private double minConfidence() {
        return rewriteProperties == null ? 0.6D : rewriteProperties.getMinConfidence();
    }
}
