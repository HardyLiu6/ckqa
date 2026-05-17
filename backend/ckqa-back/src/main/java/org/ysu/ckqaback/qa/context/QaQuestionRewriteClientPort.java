package org.ysu.ckqaback.qa.context;

/**
 * 追问独立问题改写客户端端口。
 */
public interface QaQuestionRewriteClientPort {

    QaLlmQuestionRewriteResult rewrite(String originalQuestion, QaContextAssembly context);
}
