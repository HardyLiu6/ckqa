package org.ysu.ckqaback.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户创建请求体。
 */
@Getter
@Setter
public class UserCreateRequest {

    /**
     * 用户编码。
     */
    @NotBlank(message = "userCode不能为空")
    @Size(max = 64, message = "userCode长度不能超过64")
    private String userCode;

    /**
     * 用户名。
     */
    @NotBlank(message = "username不能为空")
    @Size(max = 64, message = "username长度不能超过64")
    private String username;

    /**
     * 展示名称。
     */
    @NotBlank(message = "displayName不能为空")
    @Size(max = 128, message = "displayName长度不能超过128")
    private String displayName;

    /**
     * 密码哈希值。
     */
    @Size(max = 255, message = "passwordHash长度不能超过255")
    private String passwordHash;

    /**
     * 用户状态。
     */
    @Pattern(regexp = "active|disabled|locked|pending", message = "status取值不合法")
    private String status;
}
