package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 学生端 Hybrid v0 预热请求。
 */
@Getter
@Setter
public class QaHybridWarmupRequest {

    @NotBlank(message = "courseId不能为空")
    private String courseId;

    @NotNull(message = "knowledgeBaseId不能为空")
    private Long knowledgeBaseId;
}
