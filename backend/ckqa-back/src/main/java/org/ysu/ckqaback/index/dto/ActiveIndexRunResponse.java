package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 激活索引运行响应。
 */
@Getter
@Builder
public class ActiveIndexRunResponse {

    private final Long knowledgeBaseId;
    private final Long activeIndexRunId;
    private final Long buildRunId;
}
