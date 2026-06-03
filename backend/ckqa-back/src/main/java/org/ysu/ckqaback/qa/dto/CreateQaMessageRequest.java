package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
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
    @Pattern(regexp = "local|global|drift|basic|hybrid_v0|smart", message = "mode取值不合法")
    private String mode = "local";

    @NotBlank(message = "content不能为空")
    @Size(max = 2000, message = "content长度不能超过2000")
    private String content;

    @Valid
    private QaClientRoutingSnapshot clientRoutingSnapshot;

    @Pattern(regexp = "default|off|auto", message = "memoryPolicy取值不合法")
    private String memoryPolicy = "default";

    public CreateQaMessageRequest(String mode, String content) {
        this.mode = mode;
        this.content = content;
    }
}
