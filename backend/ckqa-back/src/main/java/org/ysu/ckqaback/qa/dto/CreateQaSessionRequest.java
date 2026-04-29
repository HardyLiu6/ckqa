package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建问答会话请求体。
 */
@Getter
@Setter
public class CreateQaSessionRequest {

    @NotNull(message = "userId不能为空")
    @Positive(message = "userId必须大于0")
    private Long userId;

    @Size(max = 64, message = "courseId长度不能超过64")
    private String courseId;

    @Positive(message = "knowledgeBaseId必须大于0")
    private Long knowledgeBaseId;

    @Pattern(regexp = "formal|smoke", message = "sessionType取值不合法")
    private String sessionType = "formal";

    @Size(max = 255, message = "title长度不能超过255")
    private String title;
}
