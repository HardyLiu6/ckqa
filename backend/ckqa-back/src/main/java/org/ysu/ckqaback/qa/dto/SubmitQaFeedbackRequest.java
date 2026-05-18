package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 学生提交问答反馈请求。
 */
@Getter
@Setter
public class SubmitQaFeedbackRequest {

    @NotNull(message = "messageId不能为空")
    @Positive(message = "messageId必须大于0")
    private Long messageId;

    @NotBlank(message = "rating不能为空")
    private String rating;

    @Size(max = 6, message = "反馈标签最多6个")
    private List<String> tags;

    @Size(max = 500, message = "反馈说明不能超过500字")
    private String comment;
}
