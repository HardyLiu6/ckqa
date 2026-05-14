package org.ysu.ckqaback.index;

import lombok.Builder;
import lombok.Getter;

import java.nio.file.Path;

/**
 * 提示词物化结果。
 * <p>
 * 描述本次构建实际写入工作区 {@code prompt/} 目录的提示词信息，供索引子进程注入
 * {@code GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE} 环境变量使用，并供后续审计读取。
 */
@Getter
@Builder
public class MaterializedPromptResult {

    /**
     * 实际生效的策略：default / graphrag_tuned / custom_pipeline。
     * 当源策略缺失资源被降级时，仍以原始策略值返回，由 {@link #fallbackReason} 标识降级原因。
     */
    private final String strategy;

    /**
     * 写入的实体抽取提示词文件绝对路径，将作为 graphrag CLI 的入参。
     */
    private final Path entityExtractionPromptFile;

    /**
     * 写入内容的 SHA-256 摘要，便于审计/对比。
     */
    private final String contentSha256;

    /**
     * 如果原策略因资源缺失而降级到 {@code default}，记录降级原因；正常情况下为 {@code null}。
     */
    private final String fallbackReason;
}
