package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ysu.ckqaback.index.dto.CandidateResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateManifestReaderTest {

    private final CandidateManifestReader reader =
            new CandidateManifestReader(new CandidateMetadataLookup(), new ObjectMapper());

    @Test
    void readsManifestWithFourCandidates(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {
                  "task": "candidate_prompt_generation",
                  "schema_version": "v1",
                  "candidates": [
                    {
                      "candidate_name": "default",
                      "source_type": "default_adapted",
                      "base_prompt_source": "prompts/extract_graph.txt",
                      "schema_used": false,
                      "fewshot_used": false,
                      "fewshot_example_count": 0,
                      "fewshot_strategy": null,
                      "generation_time": "2026-05-16T15:42:59+08:00"
                    },
                    {
                      "candidate_name": "schema_fewshot_distilled_v2_strict_tuple",
                      "source_type": "schema_fewshot_distilled_v2_strict_tuple",
                      "base_prompt_source": "prompts/candidates/auto_tuned/extract_graph.txt",
                      "schema_used": true,
                      "fewshot_used": false,
                      "fewshot_example_count": 0,
                      "fewshot_strategy": "distilled_negative_direction_rules_with_strict_tuple_guard",
                      "generation_time": "2026-05-16T15:42:59+08:00"
                    }
                  ]
                }
                """);
        // 写两份 prompt.txt 让 fallback promptSizeBytes 能读到
        Files.createDirectories(candidatesDir.resolve("default"));
        Files.writeString(candidatesDir.resolve("default/prompt.txt"), "x".repeat(2300));
        Files.createDirectories(candidatesDir.resolve("schema_fewshot_distilled_v2_strict_tuple"));
        Files.writeString(candidatesDir.resolve("schema_fewshot_distilled_v2_strict_tuple/prompt.txt"), "y".repeat(9200));

        List<CandidateResponse> candidates = reader.read(candidatesDir);

        assertThat(candidates).hasSize(2);

        CandidateResponse first = candidates.get(0);
        assertThat(first.getCandidateId()).isEqualTo("default");
        assertThat(first.getDisplayNameZh()).isEqualTo("默认基线");
        assertThat(first.getCategory()).isEqualTo("baseline");
        assertThat(first.getDescription()).isEqualTo("基线 · 课程域微调");
        assertThat(first.getIsRecommended()).isFalse();
        assertThat(first.getSchemaUsed()).isFalse();
        assertThat(first.getFewshotExampleCount()).isEqualTo(0);
        // promptSizeBytes manifest 缺失 → 从 prompt.txt 文件大小 fallback
        assertThat(first.getPromptSizeBytes()).isEqualTo(2300);
        // estimatedTokenPerCall = 2300 / 4 + 200 = 775
        assertThat(first.getEstimatedTokenPerCall()).isEqualTo(775);

        CandidateResponse second = candidates.get(1);
        assertThat(second.getCandidateId()).isEqualTo("schema_fewshot_distilled_v2_strict_tuple");
        assertThat(second.getIsRecommended()).isTrue();
        assertThat(second.getPromptSizeBytes()).isEqualTo(9200);
        // 9200 / 4 + 200 = 2500
        assertThat(second.getEstimatedTokenPerCall()).isEqualTo(2500);
        assertThat(second.getFewshotStrategy()).isEqualTo("distilled_negative_direction_rules_with_strict_tuple_guard");
    }

    @Test
    void simplifiesAbsoluteBasePromptSourcePath(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {
                  "candidates": [
                    {
                      "candidate_name": "auto_tuned",
                      "base_prompt_source": "/home/sunlight/Projects/ckqa/graphrag_pipeline/runtime/kb-build-runs/prompt-tune-cache/abc/run_5/auto_tuned/extract_graph.txt",
                      "schema_used": false
                    }
                  ]
                }
                """);
        Files.createDirectories(candidatesDir.resolve("auto_tuned"));
        Files.writeString(candidatesDir.resolve("auto_tuned/prompt.txt"), "z".repeat(3100));

        List<CandidateResponse> candidates = reader.read(candidatesDir);

        // 绝对路径只显示文件名，避免暴露服务器路径（spec § 风险 5）
        assertThat(candidates.get(0).getBasePromptSource()).isEqualTo("extract_graph.txt");
    }

    @Test
    void preservesRelativeBasePromptSource(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {
                  "candidates": [
                    {
                      "candidate_name": "default",
                      "base_prompt_source": "prompts/extract_graph.txt",
                      "schema_used": false
                    }
                  ]
                }
                """);
        Files.createDirectories(candidatesDir.resolve("default"));
        Files.writeString(candidatesDir.resolve("default/prompt.txt"), "x");

        List<CandidateResponse> candidates = reader.read(candidatesDir);

        // 相对路径原样保留
        assertThat(candidates.get(0).getBasePromptSource()).isEqualTo("prompts/extract_graph.txt");
    }

    @Test
    void skipsUnknownCandidateIds(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), """
                {
                  "candidates": [
                    { "candidate_name": "default", "schema_used": false },
                    { "candidate_name": "unknown_extra", "schema_used": true }
                  ]
                }
                """);
        Files.createDirectories(candidatesDir.resolve("default"));
        Files.writeString(candidatesDir.resolve("default/prompt.txt"), "x");

        List<CandidateResponse> candidates = reader.read(candidatesDir);

        // 未在 CandidateMetadataLookup 中的候选直接跳过（避免渲染半成品 UI）
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getCandidateId()).isEqualTo("default");
    }

    @Test
    void returnsEmptyWhenManifestMissing(@TempDir Path tmp) throws Exception {
        // 候选目录不存在或 manifest.json 缺失 → 返回空列表，让上层判断"是否需要触发生成"
        assertThat(reader.read(tmp.resolve("nonexistent"))).isEmpty();

        Files.createDirectories(tmp.resolve("empty"));
        assertThat(reader.read(tmp.resolve("empty"))).isEmpty();
    }

    @Test
    void throwsOnMalformedManifest(@TempDir Path tmp) throws Exception {
        Path candidatesDir = tmp.resolve("prompt/candidates");
        Files.createDirectories(candidatesDir);
        Files.writeString(candidatesDir.resolve("manifest.json"), "{ this is not json }");

        assertThatThrownBy(() -> reader.read(candidatesDir))
                .hasMessageContaining("manifest");
    }
}
