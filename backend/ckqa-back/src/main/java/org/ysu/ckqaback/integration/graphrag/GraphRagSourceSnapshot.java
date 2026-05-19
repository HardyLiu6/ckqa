package org.ysu.ckqaback.integration.graphrag;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Python GraphRAG 查询任务返回的来源快照。
 */
public record GraphRagSourceSnapshot(
        Integer rank,
        String kind,
        @JsonProperty("source_type") String sourceType,
        String ref,
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("document_key") String documentKey,
        @JsonProperty("source_file") String sourceFile,
        @JsonProperty("heading_path") String headingPath,
        @JsonProperty("page_start") Integer pageStart,
        @JsonProperty("page_end") Integer pageEnd,
        String snippet
) {
    public GraphRagSourceSnapshot(
            Integer rank,
            String kind,
            String ref,
            String chunkId,
            String documentKey,
            String sourceFile,
            String headingPath,
            Integer pageStart,
            Integer pageEnd,
            String snippet
    ) {
        this(
                rank,
                kind,
                kind,
                ref,
                chunkId,
                documentKey,
                sourceFile,
                headingPath,
                pageStart,
                pageEnd,
                snippet
        );
    }
}
