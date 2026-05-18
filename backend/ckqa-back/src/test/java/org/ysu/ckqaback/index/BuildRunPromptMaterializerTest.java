package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildRunPromptMaterializerTest {

    @TempDir
    Path tempDir;

    private Path graphragRoot;
    private Path buildRunsRoot;
    private BuildRunWorkspaceService workspaceService;
    private CkqaIntegrationProperties properties;
    private BuildRunPromptMaterializer materializer;

    @BeforeEach
    void setUp() throws IOException {
        graphragRoot = tempDir.resolve("graphrag");
        Files.createDirectories(graphragRoot.resolve("prompts"));
        Files.writeString(
                graphragRoot.resolve("prompts/extract_graph.txt"),
                "DEFAULT_PROMPT_BODY",
                StandardCharsets.UTF_8
        );

        buildRunsRoot = tempDir.resolve("kb-build-runs");
        Files.createDirectories(buildRunsRoot);

        properties = new CkqaIntegrationProperties();
        properties.getGraphrag().setRoot(graphragRoot.toString());
        properties.getGraphrag().setBuildRunsRoot(buildRunsRoot.toString());

        workspaceService = new BuildRunWorkspaceService(buildRunsRoot.toString());
        materializer = new BuildRunPromptMaterializer(workspaceService, properties, new ObjectMapper());
    }

    @Test
    void materialize_defaultStrategy_writesGraphragDefaultPromptIntoWorkspace() throws Exception {
        BuildRunDetailResponse buildRun = newBuildRun(7L,
                "{\"stage\":\"prompt\",\"promptConfirmed\":true,\"promptStrategy\":\"default\"}"
        );
        prepareWorkspace(buildRun);

        MaterializedPromptResult result = materializer.materialize(buildRun);

        Path target = buildRunsRoot.resolve(buildRun.getWorkspaceUri()).resolve("prompt/extract_graph.txt");
        assertThat(target).exists();
        assertThat(Files.readString(target)).isEqualTo("DEFAULT_PROMPT_BODY");
        assertThat(result.getStrategy()).isEqualTo("default");
        assertThat(result.getEntityExtractionPromptFile()).isEqualTo(target);
        assertThat(result.getFallbackReason()).isNull();
        assertThat(result.getContentSha256()).startsWith("sha256:");

        JsonNode manifest = readManifest(target.getParent());
        assertThat(manifest.get("strategy").asText()).isEqualTo("default");
        assertThat(manifest.get("contentSha256").asText()).isEqualTo(result.getContentSha256());
        assertThat(manifest.has("fallbackReason")).isFalse();
    }

    @Test
    void materialize_customPipelineStrategy_writesDraftContent() throws Exception {
        BuildRunDetailResponse buildRun = newBuildRun(8L,
                "{\"stage\":\"prompt\",\"promptConfirmed\":true,\"promptStrategy\":\"custom_pipeline\","
                        + "\"customPromptDraft\":{\"seed\":\"system_default\","
                        + "\"prompts\":{\"extract_graph\":{\"content\":\"-Goal-\\nCustom Body.\"}}}}"
        );
        prepareWorkspace(buildRun);

        MaterializedPromptResult result = materializer.materialize(buildRun);

        Path target = buildRunsRoot.resolve(buildRun.getWorkspaceUri()).resolve("prompt/extract_graph.txt");
        assertThat(Files.readString(target)).isEqualTo("-Goal-\nCustom Body.");
        assertThat(result.getStrategy()).isEqualTo("custom_pipeline");
        assertThat(result.getFallbackReason()).isNull();
    }

    @Test
    void materialize_customPipelineWithBlankDraft_throwsBadRequest() throws Exception {
        BuildRunDetailResponse buildRun = newBuildRun(9L,
                "{\"stage\":\"prompt\",\"promptStrategy\":\"custom_pipeline\","
                        + "\"customPromptDraft\":{\"prompts\":{\"extract_graph\":{\"content\":\"   \\n\"}}}}"
        );
        prepareWorkspace(buildRun);

        assertThatThrownBy(() -> materializer.materialize(buildRun))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先完成手动调优提示词构建");
    }

    @Test
    void materialize_graphragTunedWithoutSource_fallsBackToDefault() throws Exception {
        BuildRunDetailResponse buildRun = newBuildRun(10L,
                "{\"stage\":\"prompt\",\"promptStrategy\":\"graphrag_tuned\"}"
        );
        prepareWorkspace(buildRun);

        MaterializedPromptResult result = materializer.materialize(buildRun);

        Path target = buildRunsRoot.resolve(buildRun.getWorkspaceUri()).resolve("prompt/extract_graph.txt");
        assertThat(Files.readString(target)).isEqualTo("DEFAULT_PROMPT_BODY");
        assertThat(result.getStrategy()).isEqualTo("graphrag_tuned");
        assertThat(result.getFallbackReason()).isEqualTo("graphrag_tuned_source_missing");

        JsonNode manifest = readManifest(target.getParent());
        assertThat(manifest.get("fallbackReason").asText()).isEqualTo("graphrag_tuned_source_missing");
    }

    @Test
    void materialize_graphragTunedWithActiveManifest_readsActivePrompt() throws Exception {
        Files.createDirectories(graphragRoot.resolve("prompts/final/auto_tuned"));
        Files.writeString(
                graphragRoot.resolve("prompts/final/auto_tuned/extract_graph.txt"),
                "TUNED_BODY",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                graphragRoot.resolve("prompts/final/active_prompt.json"),
                "{\"candidate_name\":\"auto_tuned\","
                        + "\"active_prompt_paths\":{\"extract_graph.txt\":\"prompts/final/auto_tuned/extract_graph.txt\"}}",
                StandardCharsets.UTF_8
        );

        BuildRunDetailResponse buildRun = newBuildRun(11L,
                "{\"stage\":\"prompt\",\"promptStrategy\":\"graphrag_tuned\"}"
        );
        prepareWorkspace(buildRun);

        MaterializedPromptResult result = materializer.materialize(buildRun);

        Path target = buildRunsRoot.resolve(buildRun.getWorkspaceUri()).resolve("prompt/extract_graph.txt");
        assertThat(Files.readString(target)).isEqualTo("TUNED_BODY");
        assertThat(result.getStrategy()).isEqualTo("graphrag_tuned");
        assertThat(result.getFallbackReason()).isNull();
    }

    @Test
    void materialize_graphragTunedWithEnvFallback_readsEnvPath() throws Exception {
        Files.createDirectories(graphragRoot.resolve("prompts/final/schema_aware"));
        Files.writeString(
                graphragRoot.resolve("prompts/final/schema_aware/extract_graph.txt"),
                "SCHEMA_AWARE_BODY",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                graphragRoot.resolve(".env"),
                "GRAPHRAG_API_BASE=http://example.invalid\n"
                        + "GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE=prompts/final/schema_aware/extract_graph.txt\n",
                StandardCharsets.UTF_8
        );

        BuildRunDetailResponse buildRun = newBuildRun(12L,
                "{\"stage\":\"prompt\",\"promptStrategy\":\"graphrag_tuned\"}"
        );
        prepareWorkspace(buildRun);

        MaterializedPromptResult result = materializer.materialize(buildRun);

        Path target = buildRunsRoot.resolve(buildRun.getWorkspaceUri()).resolve("prompt/extract_graph.txt");
        assertThat(Files.readString(target)).isEqualTo("SCHEMA_AWARE_BODY");
        assertThat(result.getStrategy()).isEqualTo("graphrag_tuned");
        assertThat(result.getFallbackReason()).isNull();
    }

    @Test
    void materialize_unknownStrategy_throwsBadRequest() throws Exception {
        BuildRunDetailResponse buildRun = newBuildRun(13L,
                "{\"stage\":\"prompt\",\"promptStrategy\":\"mystery_blend\"}"
        );
        prepareWorkspace(buildRun);

        assertThatThrownBy(() -> materializer.materialize(buildRun))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知的提示词策略");
    }

    @Test
    void materialize_invalidMetadataJson_throwsBadRequest() throws Exception {
        BuildRunDetailResponse buildRun = newBuildRun(14L, "{ broken json");
        prepareWorkspace(buildRun);

        assertThatThrownBy(() -> materializer.materialize(buildRun))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("构建元数据格式无效");
    }

    @Test
    void materialize_missingMetadata_defaultsToDefaultStrategy() throws Exception {
        BuildRunDetailResponse buildRun = newBuildRun(15L, null);
        prepareWorkspace(buildRun);

        MaterializedPromptResult result = materializer.materialize(buildRun);

        assertThat(result.getStrategy()).isEqualTo("default");
        Path target = buildRunsRoot.resolve(buildRun.getWorkspaceUri()).resolve("prompt/extract_graph.txt");
        assertThat(Files.readString(target)).isEqualTo("DEFAULT_PROMPT_BODY");
    }

    @Test
    void materialize_graphragTunedWithPromptTuneCacheHit_readsCachedContent() throws Exception {
        // 预置 prompt-tune cache 文件
        Path tuneCacheDir = buildRunsRoot.resolve("prompt-tune-cache/key_abc/run_99");
        Files.createDirectories(tuneCacheDir);
        Files.writeString(tuneCacheDir.resolve("extract_graph.txt"), "PROMPT_TUNE_CACHED_BODY");

        org.ysu.ckqaback.service.CourseMaterialsService courseMaterialsService =
                org.mockito.Mockito.mock(org.ysu.ckqaback.service.CourseMaterialsService.class);
        org.ysu.ckqaback.service.MaterialObjectsService materialObjectsService =
                org.mockito.Mockito.mock(org.ysu.ckqaback.service.MaterialObjectsService.class);
        PromptTuneCacheKeyResolver realResolver =
                new PromptTuneCacheKeyResolver(courseMaterialsService, materialObjectsService);

        org.ysu.ckqaback.entity.CourseMaterials material = new org.ysu.ckqaback.entity.CourseMaterials();
        material.setId(7L);
        material.setCourseId("os");
        material.setMaterialObjectId(70L);
        org.mockito.BDDMockito.given(courseMaterialsService.getRequiredById(7L)).willReturn(material);
        org.ysu.ckqaback.entity.MaterialObjects object = new org.ysu.ckqaback.entity.MaterialObjects();
        object.setId(70L);
        object.setFileMd5("md5_a");
        org.mockito.BDDMockito.given(materialObjectsService.getById(70L)).willReturn(object);

        // 计算实际 cacheKey
        var ctx = realResolver.resolve("[7]", "os");
        Path realCacheDir = buildRunsRoot.resolve("prompt-tune-cache/" + ctx.cacheKey() + "/run_99");
        Files.createDirectories(realCacheDir);
        Files.writeString(realCacheDir.resolve("extract_graph.txt"), "PROMPT_TUNE_CACHED_BODY");

        PromptTuneService promptTuneService = org.mockito.Mockito.mock(PromptTuneService.class);
        org.ysu.ckqaback.entity.PromptTuneRuns run = new org.ysu.ckqaback.entity.PromptTuneRuns();
        run.setId(99L);
        run.setCacheKey(ctx.cacheKey());
        run.setStatus("success");
        run.setCandidateDir("prompt-tune-cache/" + ctx.cacheKey() + "/run_99");
        org.mockito.BDDMockito.given(promptTuneService.findReadyByCacheKey(ctx.cacheKey()))
                .willReturn(java.util.Optional.of(run));

        BuildRunPromptMaterializer cachedMaterializer = new BuildRunPromptMaterializer(
                workspaceService,
                properties,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                realResolver,
                promptTuneService
        );

        BuildRunDetailResponse buildRun = BuildRunDetailResponse.builder()
                .id(80L)
                .knowledgeBaseId(5L)
                .courseId("os")
                .selectedMaterialIds("[7]")
                .workspaceUri("user_0/kb_5/build_80")
                .buildMetadata("{\"stage\":\"prompt\",\"promptStrategy\":\"graphrag_tuned\"}")
                .build();
        prepareWorkspace(buildRun);

        MaterializedPromptResult result = cachedMaterializer.materialize(buildRun);

        Path target = buildRunsRoot.resolve(buildRun.getWorkspaceUri()).resolve("prompt/extract_graph.txt");
        assertThat(Files.readString(target)).isEqualTo("PROMPT_TUNE_CACHED_BODY");
        assertThat(result.getStrategy()).isEqualTo("graphrag_tuned");
        assertThat(result.getFallbackReason()).isNull();
    }

    private BuildRunDetailResponse newBuildRun(Long buildRunId, String metadata) {
        return BuildRunDetailResponse.builder()
                .id(buildRunId)
                .knowledgeBaseId(5L)
                .courseId("os")
                .workspaceUri("user_0/kb_5/build_" + buildRunId)
                .buildMetadata(metadata)
                .build();
    }

    private void prepareWorkspace(BuildRunDetailResponse buildRun) throws IOException {
        Files.createDirectories(buildRunsRoot.resolve(buildRun.getWorkspaceUri()).resolve("prompt"));
    }

    private JsonNode readManifest(Path promptDir) throws IOException {
        Path manifest = promptDir.resolve("manifest.json");
        assertThat(manifest).exists();
        return new ObjectMapper().readTree(Files.readString(manifest, StandardCharsets.UTF_8));
    }
}
