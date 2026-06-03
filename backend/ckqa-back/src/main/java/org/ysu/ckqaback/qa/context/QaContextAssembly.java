package org.ysu.ckqaback.qa.context;

/**
 * 单轮问答提交前组装出的短期上下文快照。
 */
public record QaContextAssembly(
        String strategy,
        String snapshotText,
        String messageRange,
        int charCount,
        String latestTopic,
        String latestTopicMessageRange,
        String topicSource,
        Double topicConfidence,
        String topicStackJson
) {

    public QaContextAssembly(
            String strategy,
            String snapshotText,
            String messageRange,
            int charCount,
            String latestTopic,
            String latestTopicMessageRange
    ) {
        this(strategy, snapshotText, messageRange, charCount, latestTopic, latestTopicMessageRange, "", null, "");
    }

    public boolean contextApplied() {
        return !"none".equals(strategy);
    }
}
