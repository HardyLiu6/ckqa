package org.ysu.ckqaback.index.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 索引阶段请求占位。
 */
@Getter
@Setter
public class BuildRunIndexRequest {

    private Boolean activateOnSuccess = true;

    private Boolean forceRebuild = false;
}
