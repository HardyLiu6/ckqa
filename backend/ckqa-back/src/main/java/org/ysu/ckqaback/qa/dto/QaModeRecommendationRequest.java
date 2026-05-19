package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 智能问答模式推荐请求。
 */
@Getter
@Setter
public class QaModeRecommendationRequest {

    @Size(max = 64, message = "courseId长度不能超过64")
    private String courseId;

    @Positive(message = "knowledgeBaseId必须大于0")
    private Long knowledgeBaseId;

    @Positive(message = "sessionId必须大于0")
    private Long sessionId;

    @jakarta.validation.constraints.NotBlank(message = "question不能为空")
    @Size(max = 2000, message = "question长度不能超过2000")
    private String question;

    private Boolean betaHybridEnabled = Boolean.FALSE;

    private Boolean hasConversationContext = Boolean.FALSE;
}
