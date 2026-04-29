package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库创建请求体。
 */
@Getter
@Setter
public class KnowledgeBaseCreateRequest {

    /**
     * 所属课程 ID。
     */
    @NotBlank(message = "courseId不能为空")
    @Size(max = 64, message = "courseId长度不能超过64")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "courseId只能包含字母、数字、下划线或短横线")
    private String courseId;

    /**
     * 知识库编码，同一课程内唯一。
     */
    @NotBlank(message = "kbCode不能为空")
    @Size(max = 128, message = "kbCode长度不能超过128")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "kbCode只能包含字母、数字、下划线或短横线")
    private String kbCode;

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
    @Pattern(regexp = "draft|active|archived", message = "status取值不合法")
    private String status;
}
