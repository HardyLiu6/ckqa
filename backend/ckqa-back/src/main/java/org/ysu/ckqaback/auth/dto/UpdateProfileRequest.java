package org.ysu.ckqaback.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人中心：更新当前用户资料请求。
 *
 * <p>仅允许修改 displayName；用户代码、用户名、角色、权限由管理员维护。</p>
 */
@Data
public class UpdateProfileRequest {

    @NotBlank(message = "显示名不能为空")
    @Size(min = 1, max = 128, message = "显示名长度需在 1-128 字符之间")
    private String displayName;
}
