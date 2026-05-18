package org.ysu.ckqaback.index.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 触发自动调优的请求体。
 */
@Getter
@Setter
public class PromptTuneTriggerRequest {

    /**
     * 是否强制重跑（即使已有 success 缓存也忽略）。
     */
    private Boolean force;
}
