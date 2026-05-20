package org.ysu.ckqaback.auth.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 账号 / 邮箱占用查询返回。
 *
 * <p>注册页前端可在用户输入完毕后调用以提前提示冲突；不会暴露具体冲突账号信息。</p>
 */
@Getter
@Builder
public class AccountAvailabilityResponse {

    /** 是否可用（未被占用 / 未注册）。 */
    private final boolean available;

    /**
     * 检查的字段类型：username / email。
     */
    private final String field;

    /**
     * 提示文案，便于前端直接展示。
     */
    private final String message;
}
