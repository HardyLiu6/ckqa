package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 创建问答消息请求体。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateQaMessageRequest {

    @NotBlank(message = "mode不能为空")
    @Pattern(regexp = "local|global|full", message = "mode取值不合法")
    private String mode = "local";

    @NotBlank(message = "content不能为空")
    private String content;
}
