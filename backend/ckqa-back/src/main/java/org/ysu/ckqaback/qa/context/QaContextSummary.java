package org.ysu.ckqaback.qa.context;

import org.springframework.util.StringUtils;

/**
 * 会话滚动摘要在上下文组装阶段的轻量输入。
 */
public record QaContextSummary(
        String text,
        int untilSequenceNo,
        String latestTopic,
        String latestTopicMessageRange,
        String activeTopicsJson
) {

    public QaContextSummary(String text, int untilSequenceNo) {
        this(text, untilSequenceNo, "", "", "");
    }

    public boolean hasText() {
        return StringUtils.hasText(text);
    }
}
