package org.ysu.ckqaback.qa.context;

import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * Phase 1 规则式追问改写，只处理明显指代。
 */
public class QaQuestionRewriteService {

    private static final int MAX_RETRIEVAL_QUERY_CHARS = 800;
    private static final Pattern PRONOUN_PATTERN = Pattern.compile(".*(它|这个|这一个|该概念|上面那个|前者|后者|这种|上述).*");

    public QaQuestionRewriteResult rewrite(String mode, String originalQuestion, QaContextAssembly context) {
        String question = originalQuestion == null ? "" : originalQuestion.trim();
        if (!"basic".equals(mode) && !"local".equals(mode)) {
            return noRewrite(question, "当前模式不启用 Phase 1 追问改写");
        }
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
                true,
                "命中明显指代词，并使用最近成功上文主题补全检索问题",
                context.latestTopicMessageRange()
        );
    }

    private QaQuestionRewriteResult noRewrite(String question, String reason) {
        return new QaQuestionRewriteResult(question, false, reason, "");
    }
}
