package org.ysu.ckqaback.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请邮箱验证码请求。
 */
@Data
public class EmailCodeSendRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱长度不能超过 255 字符")
    private String email;

    /**
     * 验证码场景：login（默认）/ register / reset-password。
     */
    @Pattern(regexp = "^(login|register|reset-password)$", message = "不支持的验证码场景")
    private String scene;

    /**
     * Cloudflare Turnstile token，未启用时可省略。
     */
    private String turnstileToken;
}
