package org.ysu.ckqaback.qa.context;

/**
 * 多轮问答中的轻量主题锚点，仅用于 Java 侧上下文诊断和追问补全。
 */
public record QaTopicAnchor(
        String topic,
        String source,
        String messageRange,
        double confidence
) {
}
