package org.ysu.ckqaback.qa.context;

/**
 * 主题弱绑定到课程知识图谱实体的脱敏候选项。
 * <p>
 * 该 record 会被直接序列化进运维日志，禁止加入 description/snippet/记忆原文等长内容字段。
 * </p>
 */
public record QaTopicEntityBindingCandidate(
        String id,
        String name,
        String type,
        String humanReadableId,
        Double score,
        String matchReason,
        String source
) {
}
