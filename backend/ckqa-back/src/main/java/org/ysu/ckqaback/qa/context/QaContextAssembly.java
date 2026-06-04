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
        String topicStackJson,
        String semanticStateVersion,
        String semanticStateJson
) {

    public QaContextAssembly {
        semanticStateVersion = hasText(semanticStateVersion) ? semanticStateVersion : SessionSemanticState.VERSION;
        semanticStateJson = hasText(semanticStateJson)
                ? semanticStateJson
                : SessionSemanticState.fromTopicFields(
                        latestTopic,
                        latestTopicMessageRange,
                        topicSource,
                        topicConfidence,
                        topicStackJson
                ).json();
    }

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

    public QaContextAssembly(
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
        this(
                strategy,
                snapshotText,
                messageRange,
                charCount,
                latestTopic,
                latestTopicMessageRange,
                topicSource,
                topicConfidence,
                topicStackJson,
                null,
                null
        );
    }

    public boolean contextApplied() {
        return !"none".equals(strategy);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
