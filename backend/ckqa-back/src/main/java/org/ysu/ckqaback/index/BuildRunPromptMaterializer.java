package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 把构建流水线选择的实体抽取提示词物化到 build run 工作区。
 * <p>
 * 写入路径固定为 {@code <workspace>/prompt/extract_graph.txt}，
 * 同时写一份 {@code <workspace>/prompt/manifest.json} 用作审计快照。
 * <p>
 * 三种策略的源数据来源：
 * <ul>
 *   <li>{@code default}：复制 {@code ${GRAPHRAG_ROOT}/prompts/extract_graph.txt}</li>
 *   <li>{@code graphrag_tuned}：依次尝试 {@code prompts/final/active_prompt.json} 指向的文件、
 *       {@code .env} 中 {@code GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE} 指向的文件，
 *       两者都缺失时降级为 {@code default} 并写入 {@code fallbackReason}</li>
 *   <li>{@code custom_pipeline}：从 build_metadata 的 {@code customPromptDraft.prompts.extract_graph.content}
 *       直接落盘；草稿缺失时抛 400</li>
 * </ul>
 */
@Service
public class BuildRunPromptMaterializer {

    private static final Logger log = LoggerFactory.getLogger(BuildRunPromptMaterializer.class);

    private static final String DEFAULT_RELATIVE_PROMPT = "prompts/extract_graph.txt";
    private static final String ACTIVE_PROMPT_RELATIVE = "prompts/final/active_prompt.json";

    private final BuildRunWorkspaceService workspaceService;
    private final CkqaIntegrationProperties properties;
    private final ObjectMapper objectMapper;

    @Autowired
    public BuildRunPromptMaterializer(
            BuildRunWorkspaceService workspaceService,
            CkqaIntegrationProperties properties
    ) {
        this(workspaceService, properties, new ObjectMapper());
    }

    BuildRunPromptMaterializer(
            BuildRunWorkspaceService workspaceService,
            CkqaIntegrationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.workspaceService = workspaceService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 build run 的 promptStrategy，落盘并返回物化结果。
     */
    public MaterializedPromptResult materialize(BuildRunDetailResponse buildRun) throws IOException {
        if (buildRun == null) {
            throw new IllegalArgumentException("buildRun 不能为空");
        }
        Path promptDir = workspaceService.resolve(buildRun.getWorkspaceUri()).resolve("prompt");
        Files.createDirectories(promptDir);

        Path target = promptDir.resolve("extract_graph.txt");
        JsonNode metadata = parseMetadata(buildRun.getBuildMetadata());
        String strategy = readStrategy(metadata);

        String content;
        String fallbackReason = null;
        switch (strategy) {
            case "custom_pipeline" -> content = readCustomDraftContent(metadata);
            case "graphrag_tuned" -> {
                String tuned = readGraphRagTunedContent();
                if (tuned == null) {
                    fallbackReason = "graphrag_tuned_source_missing";
                    content = readDefaultPromptContent();
                } else {
                    content = tuned;
                }
            }
            case "default" -> content = readDefaultPromptContent();
            default -> throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    "未知的提示词策略: " + strategy
            );
        }

        Files.writeString(target, content, StandardCharsets.UTF_8);
        String contentSha256 = sha256(content);
        writeManifest(promptDir, buildRun, strategy, contentSha256, fallbackReason);

        if (fallbackReason != null) {
            log.warn(
                    "build_run={} 提示词策略 {} 源文件缺失，已降级到 default：{}",
                    buildRun.getId(),
                    strategy,
                    fallbackReason
            );
        }

        return MaterializedPromptResult.builder()
                .strategy(strategy)
                .entityExtractionPromptFile(target)
                .contentSha256(contentSha256)
                .fallbackReason(fallbackReason)
                .build();
    }

    private JsonNode parseMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(metadataJson);
        } catch (JsonProcessingException exception) {
            // 与 IndexWorkflowService.assertPromptConfirmed 保持一致：metadata 损坏直接拒绝。
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    "构建元数据格式无效"
            );
        }
    }

    private String readStrategy(JsonNode metadata) {
        JsonNode node = metadata.path("promptStrategy");
        if (!node.isTextual() || !StringUtils.hasText(node.asText())) {
            return "default";
        }
        return node.asText().trim().toLowerCase(Locale.ROOT);
    }

    private String readCustomDraftContent(JsonNode metadata) {
        JsonNode contentNode = metadata
                .path("customPromptDraft")
                .path("prompts")
                .path("extract_graph")
                .path("content");
        if (!contentNode.isTextual() || contentNode.asText().strip().isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    "请先完成手动调优提示词构建"
            );
        }
        return contentNode.asText();
    }

    private String readDefaultPromptContent() throws IOException {
        Path source = resolveGraphRagPath(DEFAULT_RELATIVE_PROMPT);
        if (source == null || !Files.isRegularFile(source)) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "默认实体抽取提示词文件不存在: " + DEFAULT_RELATIVE_PROMPT
            );
        }
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    /**
     * 优先读取 active_prompt.json 指向的文件，找不到时回退到 .env 中
     * GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE 指向的路径；都不可用则返回 null 让上层降级。
     */
    private String readGraphRagTunedContent() throws IOException {
        Path activeFromManifest = readActiveExtractGraphPath();
        if (activeFromManifest != null && Files.isRegularFile(activeFromManifest)) {
            return Files.readString(activeFromManifest, StandardCharsets.UTF_8);
        }
        Path activeFromEnv = readEnvExtractGraphPath();
        if (activeFromEnv != null && Files.isRegularFile(activeFromEnv)) {
            return Files.readString(activeFromEnv, StandardCharsets.UTF_8);
        }
        return null;
    }

    private Path readActiveExtractGraphPath() {
        Path manifest = resolveGraphRagPath(ACTIVE_PROMPT_RELATIVE);
        if (manifest == null || !Files.isRegularFile(manifest)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(manifest, StandardCharsets.UTF_8));
            JsonNode pathNode = root.path("active_prompt_paths").path("extract_graph.txt");
            if (!pathNode.isTextual()) {
                return null;
            }
            return resolveGraphRagPath(pathNode.asText());
        } catch (IOException exception) {
            log.warn("解析 active_prompt.json 失败: {}", exception.getMessage());
            return null;
        }
    }

    private Path readEnvExtractGraphPath() {
        Path envFile = resolveGraphRagPath(".env");
        if (envFile == null || !Files.isRegularFile(envFile)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (!trimmed.startsWith("GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE=")) {
                    continue;
                }
                String value = trimmed.substring("GRAPHRAG_ENTITY_EXTRACTION_PROMPT_FILE=".length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!StringUtils.hasText(value)) {
                    return null;
                }
                return resolveGraphRagPath(value);
            }
        } catch (IOException exception) {
            log.warn("读取 graphrag_pipeline/.env 失败: {}", exception.getMessage());
        }
        return null;
    }

    private Path resolveGraphRagPath(String relativeOrAbsolute) {
        if (!StringUtils.hasText(relativeOrAbsolute)) {
            return null;
        }
        String graphragRoot = properties.getGraphrag().getRoot();
        if (!StringUtils.hasText(graphragRoot)) {
            return null;
        }
        Path candidate = Path.of(relativeOrAbsolute);
        if (!candidate.isAbsolute()) {
            candidate = Path.of(graphragRoot).resolve(candidate);
        }
        return candidate.normalize();
    }

    private void writeManifest(
            Path promptDir,
            BuildRunDetailResponse buildRun,
            String strategy,
            String contentSha256,
            String fallbackReason
    ) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("buildRunId", buildRun.getId());
        manifest.put("knowledgeBaseId", buildRun.getKnowledgeBaseId());
        manifest.put("courseId", buildRun.getCourseId());
        manifest.put("strategy", strategy);
        manifest.put("entityExtractionPromptFile", "prompt/extract_graph.txt");
        manifest.put("contentSha256", contentSha256);
        if (fallbackReason != null) {
            manifest.put("fallbackReason", fallbackReason);
        }
        Files.writeString(
                promptDir.resolve("manifest.json"),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest),
                StandardCharsets.UTF_8
        );
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("sha256:");
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "sha256:unavailable";
        }
    }
}
