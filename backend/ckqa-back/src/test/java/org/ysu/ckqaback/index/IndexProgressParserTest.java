package org.ysu.ckqaback.index;

import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.index.dto.IndexProgress;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IndexProgressParserTest {

    private final IndexProgressParser parser = new IndexProgressParser();

    @Test
    void shouldReturnEmptyWhenLogIsEmpty() {
        Optional<IndexProgress> progress = parser.parse(
                Path.of("src/test/resources/fixtures/graphrag/process-empty.log.txt"));
        assertThat(progress).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenLogPathMissing() {
        Optional<IndexProgress> progress = parser.parse(Path.of("/nonexistent/path/process.log"));
        assertThat(progress).isEmpty();
    }

    @Test
    void shouldParseRunningExtractGraphSnapshot() {
        Optional<IndexProgress> maybe = parser.parse(
                Path.of("src/test/resources/fixtures/graphrag/process-running-extract-graph.log.txt"));
        assertThat(maybe).isPresent();
        IndexProgress progress = maybe.get();

        assertThat(progress.getPipelineWorkflows()).containsExactly(
                "load_input_documents", "create_base_text_units", "create_final_documents",
                "extract_graph", "finalize_graph", "create_communities",
                "create_final_text_units", "create_community_reports", "generate_text_embeddings");
        assertThat(progress.getCurrentWorkflowKey()).isEqualTo("extract_graph");
        assertThat(progress.getCurrentWorkflowIndex()).isEqualTo(3);
        assertThat(progress.getCompletedWorkflowKeys()).containsExactly(
                "load_input_documents", "create_base_text_units", "create_final_documents");
        assertThat(progress.getSubProgress()).isNotNull();
        assertThat(progress.getSubProgress().getCurrent()).isEqualTo(128);
        assertThat(progress.getSubProgress().getTotal()).isEqualTo(533);
        // 累计权重：1+2+1=4；当前 extract_graph 子进度 128/533≈0.240，weight 22 * 0.240 ≈ 5.28
        // 分子约 9.28，分母为本次 pipeline 涉及的工作流权重之和
        assertThat(progress.getPercentage()).isBetween(8, 12);
    }

    @Test
    void shouldClampPercentageBetweenZeroAndNinetyNine() {
        Optional<IndexProgress> maybe = parser.parse(
                Path.of("src/test/resources/fixtures/graphrag/process-running-extract-graph.log.txt"));
        assertThat(maybe).isPresent();
        assertThat(maybe.get().getPercentage()).isBetween(0, 99);
    }
}
