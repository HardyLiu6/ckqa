package org.ysu.ckqaback.integration.graphrag;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Python GraphRAG 查询任务返回的来源快照。
 */
public record GraphRagSourceSnapshot(
        Integer rank,
        String kind,
        String ref,
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("document_key") String documentKey,
        @JsonProperty("source_file") String sourceFile,
        @JsonProperty("heading_path") String headingPath,
        @JsonProperty("page_start") Integer pageStart,
        @JsonProperty("page_end") Integer pageEnd,
        String snippet
) {
}
