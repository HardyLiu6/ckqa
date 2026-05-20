package org.ysu.ckqaback.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 通过邮箱验证码重置密码的请求。
 */
@Data
public class PasswordResetRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱长度不能超过 255 字符")
    private String email;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{4,8}$", message = "验证码需为 4-8 位数字")
    private String code;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度必须在 8 到 72 位之间")
    private String newPassword;
}
