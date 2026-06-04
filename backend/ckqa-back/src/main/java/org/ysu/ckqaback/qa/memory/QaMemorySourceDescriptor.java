package org.ysu.ckqaback.qa.memory;

/**
 * 长期记忆治理来源描述，只包含可排障的脱敏字段，不包含记忆正文。
 */
public record QaMemorySourceDescriptor(
        Long memoryId,
        String memoryType,
        Long sourceSessionId,
        Long sourceMessageId,
        String includeReason,
        String textHash,
        int textChars
) {
}
