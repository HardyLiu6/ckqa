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
        String latestTopicMessageRange
) {

    public boolean contextApplied() {
        return !"none".equals(strategy);
    }
}
