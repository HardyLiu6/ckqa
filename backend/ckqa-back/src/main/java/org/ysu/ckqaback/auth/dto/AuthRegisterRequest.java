package org.ysu.ckqaback.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 学生注册请求。
 */
@Getter
@Setter
public class AuthRegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "[a-zA-Z0-9._-]{4,64}", message = "用户名仅支持4到64位字母、数字、点、下划线和短横线")
    private String username;

    @NotBlank(message = "展示名称不能为空")
    @Size(max = 128, message = "展示名称长度不能超过128")
    private String displayName;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度必须在8到72位之间")
    private String password;

    /**
     * 注册时绑定的邮箱（可选）。
     * <p>当传入 email 时必须同时提供 emailCode；后端会校验验证码是否与 register 场景匹配，并在写库前确认邮箱未被占用。</p>
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱长度不能超过 255 字符")
    private String email;

    /**
     * 邮箱验证码（与 email 配对使用）。
     */
    @Pattern(regexp = "^\\d{4,8}$", message = "验证码需为 4-8 位数字")
    private String emailCode;
}
