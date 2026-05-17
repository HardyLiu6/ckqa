package org.ysu.ckqaback.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人中心：修改当前用户密码请求。
 *
 * <p>校验顺序：
 * <ol>
 *   <li>oldPassword 非空 + 与库内 hash 匹配</li>
 *   <li>newPassword 非空 + 长度在 8-64 之间</li>
 *   <li>newPassword 与 oldPassword 不同</li>
 * </ol>
 * 复杂度（含字母数字符号）由 service 层校验。</p>
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "新密码长度需在 8-64 字符之间")
    private String newPassword;
}
