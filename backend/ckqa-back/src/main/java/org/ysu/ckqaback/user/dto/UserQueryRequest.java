package org.ysu.ckqaback.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户分页查询请求参数。
 */
@Getter
@Setter
public class UserQueryRequest {

    /**
     * 当前页码。
     */
    @Min(value = 1, message = "page必须大于等于1")
    private Long page = 1L;

    /**
     * 每页条数。
     */
    @Min(value = 1, message = "size必须大于等于1")
    @Max(value = 100, message = "size不能大于100")
    private Long size = 10L;

    /**
     * 用户名模糊查询条件。
     */
    @Size(max = 64, message = "username长度不能超过64")
    private String username;

    /**
     * 用户编码、用户名、展示名统一模糊查询条件。
     */
    @Size(max = 128, message = "keyword长度不能超过128")
    private String keyword;

    /**
     * 用户状态过滤条件。
     */
    @Pattern(regexp = "active|disabled|locked|pending", message = "status取值不合法")
    private String status;

    /**
     * 平台角色过滤条件。
     */
    @Pattern(regexp = "student|teacher|admin", message = "roleCode取值不合法")
    private String roleCode;
}
