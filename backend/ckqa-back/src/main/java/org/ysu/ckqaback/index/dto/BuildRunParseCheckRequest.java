package org.ysu.ckqaback.index.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 解析检查请求。
 */
@Getter
@Setter
public class BuildRunParseCheckRequest {

    private Boolean parseMissing = false;
}
