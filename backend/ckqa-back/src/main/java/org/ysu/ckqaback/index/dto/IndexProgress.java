package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GraphRAG 索引实时进度。仅在 BuildRun.currentStage="index" 且 status="running"
 * 时由后端从 process.log 解析后填入 {@link BuildRunDetailResponse}。
 *
 * <p>设计为 100% 来自日志解析：前端无需理解权重表，直接拿 {@link #percentage}
 * 渲染进度条，拿 {@link #currentWorkflowKey} 渲染当前阶段名。</p>
 */
@Getter
@Builder
public class IndexProgress {
    /** 本次 pipeline 实际执行的有序 workflow 列表（已剔除被关闭的工作流）。 */
    private final List<String> pipelineWorkflows;
    /** 当前正在执行的 workflow 在 pipelineWorkflows 中的下标，从 0 开始；全部完成时等于 size-1。 */
    private final int currentWorkflowIndex;
    /** 当前正在执行的 workflow key；全部完成时为 pipelineWorkflows 最后一个。 */
    private final String currentWorkflowKey;
    /** 已完成的 workflow key 列表，按完成顺序。 */
    private final List<String> completedWorkflowKeys;
    /** 当前 workflow 的子进度；不存在时为 null。 */
    private final SubProgress subProgress;
    /** 加权百分比 [0, 100]。 */
    private final int percentage;

    @Getter
    @Builder
    public static class SubProgress {
        private final int current;
        private final int total;
    }
}
