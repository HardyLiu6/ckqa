package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理端问答运维日志查询条件。
 */
@Getter
@Setter
public class QaOperationsQueryRequest {

    private String courseId;
    private Long knowledgeBaseId;

    @Pattern(regexp = "local|global|drift|basic|hybrid_v0", message = "mode取值不合法")
    private String mode;

    @Pattern(regexp = "pending|running|success|failed|stale|legacy", message = "taskStatus取值不合法")
    private String taskStatus;

    @Pattern(regexp = "helpful|unhelpful|needs_improvement", message = "feedbackRating取值不合法")
    private String feedbackRating;

    @Pattern(regexp = "source_irrelevant|too_long|wants_example|unclear|incorrect|other", message = "feedbackTag取值不合法")
    private String feedbackTag;

    private String createdFrom;
    private String createdTo;

    @Min(value = 1, message = "page必须大于0")
    private long page = 1;

    @Min(value = 1, message = "size必须大于0")
    @Max(value = 100, message = "size最大为100")
    private long size = 20;
}
