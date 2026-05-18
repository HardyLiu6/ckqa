package org.ysu.ckqaback.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人中心：更新当前用户资料请求。
 *
 * <p>可修改字段：displayName / email / phone。
 * 用户代码、用户名、角色、权限由管理员维护。</p>
 *
 * <p>所有字段使用「null = 不动 / 空串 = 清空 / 有值 = 更新」三态语义。
 * displayName 不允许为空字符串（业务上必须有显示名）。</p>
 */
@Data
public class UpdateProfileRequest {

    @NotBlank(message = "显示名不能为空")
    @Size(min = 1, max = 128, message = "显示名长度需在 1-128 字符之间")
    private String displayName;

    /**
     * 联系邮箱：null 不动 / 空串清空 / 有值需要符合 RFC5322 格式。
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱长度不能超过 255 字符")
    private String email;

    /**
     * 联系手机号：null 不动 / 空串清空 / 有值需要 8-15 位纯数字（可选 + 前缀）。
     */
    @Pattern(
            regexp = "^$|^\\+?\\d{8,15}$",
            message = "手机号需为 8-15 位纯数字，可选 '+' 前缀"
    )
    @Size(max = 32, message = "手机号长度不能超过 32 字符")
    private String phone;
}
