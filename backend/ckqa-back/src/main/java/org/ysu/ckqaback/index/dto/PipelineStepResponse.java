package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 通用流水线步骤触发响应。
 * <p>
 * 用于 POST /prompt-tune-samples、POST /candidates、POST /extraction-eval 等
 * 异步触发型端点，反馈本次触发的状态摘要。
 * </p>
 */
@Getter
@Builder
public class PipelineStepResponse {

    /** 关联的构建流水线 ID。 */
    private final Long buildRunId;

    /** queued / running / done / failed。 */
    private final String status;

    /** 该步骤产物计数（如样本数、候选数等），无产物时可为 null。 */
    private final Integer producedCount;

    /** 该步骤耗时（秒），同步执行时填充，异步触发可为 null。 */
    private final Integer elapsedSeconds;

    /** 触发结果说明或失败摘要。 */
    private final String message;

    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
}
