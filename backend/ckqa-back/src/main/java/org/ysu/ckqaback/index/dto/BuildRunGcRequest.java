package org.ysu.ckqaback.index.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 构建流水线清理请求。
 */
@Getter
@Setter
public class BuildRunGcRequest {

    private Boolean deleteWorkspace = false;

    private Boolean dryRun = true;
}
