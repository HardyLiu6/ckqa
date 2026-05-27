package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 问题领域校验请求。
 */
@Getter
@Setter
public class QaQuestionDomainCheckRequest {

    @Size(max = 64, message = "courseId长度不能超过64")
    private String courseId;

    @Positive(message = "knowledgeBaseId必须大于0")
    private Long knowledgeBaseId;

    @Positive(message = "sessionId必须大于0")
    private Long sessionId;

    @NotBlank(message = "question不能为空")
    @Size(max = 2000, message = "question长度不能超过2000")
    private String question;

    private Boolean hasConversationContext = Boolean.FALSE;
}
