package org.ysu.ckqaback.integration.graphrag;

import lombok.Builder;
import lombok.Getter;

/**
 * 索引运行元数据。
 */
@Getter
@Builder
public class IndexRunMetadata {

    private final String command;
    private final Long elapsedSeconds;
    private final Integer exitCode;
    private final String errorSummary;
    private final Boolean staleTimeoutRecovered;
}
