package org.ysu.ckqaback.auth.dto;

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
}
