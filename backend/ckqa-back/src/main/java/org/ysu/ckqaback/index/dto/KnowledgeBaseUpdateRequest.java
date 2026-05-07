package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库基础信息更新请求体。
 */
@Getter
@Setter
public class KnowledgeBaseUpdateRequest {

    /**
     * 知识库名称。
     */
    @NotBlank(message = "name不能为空")
    @Size(max = 255, message = "name长度不能超过255")
    private String name;

    /**
     * 知识库说明。
     */
    @Size(max = 2000, message = "description长度不能超过2000")
    private String description;

    /**
     * 知识库状态。
     */
    @NotBlank(message = "status不能为空")
    @Pattern(regexp = "draft|active|archived", message = "status取值不合法")
    private String status;
}
