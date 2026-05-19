package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 学生端会话列表查询参数。
 */
@Getter
@Setter
public class QaSessionQueryRequest {

    @Size(max = 64, message = "courseId长度不能超过64")
    private String courseId;

    @Positive(message = "knowledgeBaseId必须大于0")
    private Long knowledgeBaseId;

    @Pattern(regexp = "active|archived|deleted", message = "status取值不合法")
    private String status = "active";

    @Positive(message = "page必须大于0")
    private Long page = 1L;

    @Positive(message = "size必须大于0")
    private Long size = 20L;
}
