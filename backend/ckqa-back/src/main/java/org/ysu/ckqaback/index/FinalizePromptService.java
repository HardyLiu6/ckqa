package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptDrafts;
import org.ysu.ckqaback.entity.PromptTuneExtractionEvalRuns;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.BuildRunDetailResponse;
import org.ysu.ckqaback.index.dto.FinalizePromptRequest;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptDraftsService;
import org.ysu.ckqaback.service.PromptTuneExtractionEvalRunsService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 05 步 finalize 业务编排：
 * <ol>
 *   <li>校验 04 评分 run（status=success）+ candidateId 在评分报告中（4110 / 4111）。</li>
 *   <li><b>直接写</b> {@code customPromptDraft}（不复用 KnowledgeBaseBuildRunService.saveCustomPromptDraft）：
 *       完整快照含 selectedCandidateId / compositeScore / sourceEvalRunId / finalizedAt / prompts.extract_graph.content。</li>
 *   <li>当 saveAsDraft=true 时同事务插一条 prompt_drafts；mybatis-plus.save 返回 false 立即抛 5000 让事务回滚。</li>
 * </ol>
 *
 * <p>错误码语义：</p>
 * <ul>
 *   <li>{@code 4110 EXTRACTION_EVAL_NOT_SUCCESS}：评分尚未成功（业务前提不满足）。</li>
 *   <li>{@code 4111 INVALID_FINALIZE_CANDIDATE}：candidateId 不在评分报告 candidates 中（业务入参错误）。</li>
 *   <li>{@code 5000 INTERNAL_ERROR}：reportJson 解析失败 / candidate prompt 文件读失败 / prompt_drafts.save 返回 false（服务端数据异常，与 4111 业务入参错误明确区分）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FinalizePromptService {

    private static final Logger log = LoggerFactory.getLogger(FinalizePromptService.class);

    private final KnowledgeBaseBuildRunsService buildRunsService;
    private final PromptTuneExtractionEvalRunsService evalRunsService;
    private final PromptDraftsService promptDraftsService;
    private final BuildRunWorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    @Transactional
    public BuildRunDetailResponse finalizePrompt(Long buildRunId, FinalizePromptRequest request) {
        KnowledgeBaseBuildRuns buildRun = buildRunsService.getRequiredById(buildRunId);

        // 校验 1：04 评分必须 success
        Optional<PromptTuneExtractionEvalRuns> evalOpt = evalRunsService.findLatestByBuildRunId(buildRunId);
        if (evalOpt.isEmpty() || !"success".equals(evalOpt.get().getStatus())) {
            throw new BusinessException(
                    ApiResultCode.EXTRACTION_EVAL_NOT_SUCCESS,
                    HttpStatus.BAD_REQUEST,
                    evalOpt.isEmpty() ? "尚未触发评分" : "评分当前状态：" + evalOpt.get().getStatus()
            );
        }
        PromptTuneExtractionEvalRuns evalRun = evalOpt.get();

        // 解析 reportJson + 校验 candidateId（区分 4111 业务入参错误 vs 5000 服务端数据异常）
        JsonNode reportRoot = parseReportJson(evalRun.getReportJson());
        BigDecimal compositeScore = lookupCompositeScore(reportRoot, request.getCandidateId());
        if (compositeScore == null) {
            throw new BusinessException(
                    ApiResultCode.INVALID_FINALIZE_CANDIDATE,
                    HttpStatus.BAD_REQUEST,
                    "未识别的候选 ID：" + request.getCandidateId()
            );
        }

        // 读候选 prompt 全文
        Path workspace = workspaceService.resolve(buildRun.getWorkspaceUri());
        Path promptFile = workspace.resolve("prompt").resolve("candidates")
                .resolve(request.getCandidateId()).resolve("prompt.txt");
        String promptContent = readPromptText(promptFile, request.getCandidateId());

        // 直接写 customPromptDraft：完整 finalize 快照（不复用 saveCustomPromptDraft）
        String currentSeed = readCurrentSeed(buildRun);
        if (currentSeed == null) currentSeed = "system_default";
        String mergedMetadata = mergeFinalizedDraftIntoMetadata(
                buildRun.getBuildMetadata(),
                currentSeed,
                request.getCandidateId(),
                promptContent,
                compositeScore,
                evalRun.getId()
        );
        buildRun.setBuildMetadata(mergedMetadata);
        buildRun.setUpdatedAt(LocalDateTime.now());
        buildRunsService.updateById(buildRun);

        // 可选：插 prompt_drafts
        if (Boolean.TRUE.equals(request.getSaveAsDraft())) {
            PromptDrafts draft = new PromptDrafts();
            draft.setKnowledgeBaseId(buildRun.getKnowledgeBaseId());
            draft.setName(defaultText(request.getDraftName(),
                    "draft-" + buildRunId + "-" + request.getCandidateId()));
            draft.setDescription(request.getDraftDescription());
            draft.setSeed(currentSeed);
            draft.setCandidateId(request.getCandidateId());
            draft.setPromptsJson(serializePrompts(promptContent));
            draft.setSourceBuildRunId(buildRunId);
            draft.setCompositeScore(compositeScore);
            LocalDateTime now = LocalDateTime.now();
            draft.setCreatedAt(now);
            draft.setUpdatedAt(now);
            // mybatis-plus save 返回 false 时主动抛错，让 @Transactional 回滚 customPromptDraft 写入
            boolean saved = promptDraftsService.save(draft);
            if (!saved) {
                throw new BusinessException(
                        ApiResultCode.INTERNAL_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "历史草稿入库失败（save returned false），已回滚 build run customPromptDraft 写入"
                );
            }
            log.info("历史草稿入库 kbId={} draftId={} sourceBuildRunId={}",
                    buildRun.getKnowledgeBaseId(), draft.getId(), buildRunId);
        }

        return BuildRunDetailResponse.fromEntity(buildRunsService.getRequiredById(buildRunId));
    }

    /**
     * 解析 reportJson；解析失败抛 5000（服务端数据异常，与 4111 业务入参错误区分）。
     */
    private JsonNode parseReportJson(String reportJson) {
        if (reportJson == null || reportJson.isBlank()) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "评分报告内容为空，无法 finalize"
            );
        }
        try {
            return objectMapper.readTree(reportJson);
        } catch (JsonProcessingException e) {
            log.warn("解析 reportJson 失败: {}", e.getMessage());
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "评分报告 JSON 解析失败：" + e.getMessage()
            );
        }
    }

    /**
     * 在已解析的 reportRoot 中找 candidateId 对应的 composite_score。
     * 找不到返 null（caller 抛 4111）；解析阶段已经成功，这里只做白名单匹配。
     */
    private BigDecimal lookupCompositeScore(JsonNode reportRoot, String candidateId) {
        JsonNode arr = reportRoot.path("all_candidates_ranked");
        if (!arr.isArray()) return null;
        for (JsonNode entry : arr) {
            String id = entry.path("candidate").asText(null);
            if (candidateId.equals(id)) {
                JsonNode score = entry.path("composite_score");
                if (score.isNumber()) return new BigDecimal(score.asText());
                return BigDecimal.ZERO;
            }
        }
        return null;
    }

    private String readPromptText(Path promptFile, String candidateId) {
        if (!Files.exists(promptFile)) {
            // 文件不存在视为服务端数据异常（评分报告认了这个 candidate，但磁盘上没产物），抛 5000
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "候选 prompt 文件不存在 candidate=" + candidateId
            );
        }
        try {
            return Files.readString(promptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取候选 prompt 失败: " + e.getMessage()
            );
        }
    }

    private String readCurrentSeed(KnowledgeBaseBuildRuns buildRun) {
        String metadata = buildRun.getBuildMetadata();
        if (metadata == null || metadata.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(metadata).path("customPromptDraft").path("seed");
            return node.isTextual() ? node.asText() : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 把 finalize 完整快照合并进 buildMetadata.customPromptDraft，<b>只</b>替换 customPromptDraft 子节点，
     * 保留 stage / promptStrategy / promptConfirmed / exportConfirmed / graphInputConfirmed 等其他键。
     */
    private String mergeFinalizedDraftIntoMetadata(
            String existingMetadataJson,
            String seed,
            String candidateId,
            String promptContent,
            BigDecimal compositeScore,
            Long evalRunId
    ) {
        ObjectNode root;
        if (existingMetadataJson == null || existingMetadataJson.isBlank()) {
            root = objectMapper.createObjectNode();
        } else {
            try {
                JsonNode parsed = objectMapper.readTree(existingMetadataJson);
                root = parsed.isObject() ? (ObjectNode) parsed : objectMapper.createObjectNode();
            } catch (JsonProcessingException e) {
                log.warn("旧 metadata 解析失败，按空对象重写 customPromptDraft：{}", e.getMessage());
                root = objectMapper.createObjectNode();
            }
        }

        // 保留旧 customPromptDraft 中跨阶段不变的字段（seedSnapshotAt / 旧 modifiedAt / baseHash 等），
        // 同时整体替换 prompts.extract_graph.content + 注入 finalize 快照。
        JsonNode oldDraftNode = root.path("customPromptDraft");
        ObjectNode draft = objectMapper.createObjectNode();
        draft.put("seed", seed);
        if (oldDraftNode.has("seedSnapshotAt") && oldDraftNode.path("seedSnapshotAt").isTextual()) {
            draft.put("seedSnapshotAt", oldDraftNode.path("seedSnapshotAt").asText());
        }

        // finalize 快照核心字段
        draft.put("selectedCandidateId", candidateId);
        draft.put("compositeScore", compositeScore.toPlainString());
        draft.put("sourceEvalRunId", evalRunId);
        draft.put("finalizedAt", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        draft.put("updatedAt", LocalDateTime.now().toString());

        // prompts.extract_graph.content 整体覆盖
        ObjectNode prompts = objectMapper.createObjectNode();
        ObjectNode extractGraph = objectMapper.createObjectNode();
        extractGraph.put("content", promptContent);
        extractGraph.put("modifiedAt", LocalDateTime.now().toString());
        // 保留 baseHash 旧值（如有）
        if (oldDraftNode.path("prompts").path("extract_graph").path("baseHash").isTextual()) {
            extractGraph.put("baseHash",
                    oldDraftNode.path("prompts").path("extract_graph").path("baseHash").asText());
        }
        prompts.set("extract_graph", extractGraph);
        draft.set("prompts", prompts);

        root.set("customPromptDraft", draft);

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "序列化 customPromptDraft 失败：" + e.getMessage()
            );
        }
    }

    private String serializePrompts(String extractGraphContent) {
        Map<String, String> prompts = new LinkedHashMap<>();
        prompts.put("extract_graph", extractGraphContent);
        try {
            return objectMapper.writeValueAsString(prompts);
        } catch (JsonProcessingException e) {
            throw new BusinessException(
                    ApiResultCode.INTERNAL_ERROR,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "序列化 prompts 失败"
            );
        }
    }

    private static String defaultText(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
