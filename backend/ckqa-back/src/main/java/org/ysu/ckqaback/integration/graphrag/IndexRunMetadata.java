package org.ysu.ckqaback.integration.graphrag;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 索引运行元数据。
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IndexRunMetadata {

    private final String command;
    private final Long elapsedSeconds;
    private final Integer exitCode;
    private final String errorSummary;
    private final Boolean staleTimeoutRecovered;
    private final String promptStrategy;
    private final String promptContentSha256;
    private final String promptFallbackReason;
    /**
     * 索引产物摘要：图谱体量 + 阶段耗时分布。仅 success 状态写入。
     * 由 utils/index_summary.py 读取 parquet / stats.json 生成；解析失败时为 null，
     * 不影响 indexRun 的成功判定。
     */
    private final GraphSummary graphSummary;

    @Getter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GraphSummary {
        private final Integer entityCount;
        private final Integer relationshipCount;
        private final Integer communityCount;
        private final Integer communityReportCount;
        private final Integer documentCount;
        private final Integer textUnitCount;
        private final Double totalRuntimeSeconds;
        /** 各 workflow 实际耗时（秒）。键为 GraphRAG workflow key，例如 extract_graph。 */
        private final Map<String, Double> workflowDurations;
    }
}

