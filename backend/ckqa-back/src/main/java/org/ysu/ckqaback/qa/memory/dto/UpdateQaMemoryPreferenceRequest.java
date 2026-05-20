package org.ysu.ckqaback.qa.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新长期记忆偏好请求。
 */
@Getter
@Setter
public class UpdateQaMemoryPreferenceRequest {

    @NotBlank(message = "courseId不能为空")
    private String courseId;

    @NotNull(message = "knowledgeBaseId不能为空")
    @Positive(message = "knowledgeBaseId必须大于0")
    private Long knowledgeBaseId;

    @NotNull(message = "enabled不能为空")
    private Boolean enabled;
}
