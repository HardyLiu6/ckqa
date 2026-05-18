package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;
import org.ysu.ckqaback.index.dto.AuditSampleUpdateRequest;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 标注样本业务编排服务。
 * <p>
 * 负责 audit 样本的生成、查询、更新等业务流程编排。
 * 不使用 @Transactional，事务边界由底层持久化服务控制。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditSampleService {

    private final PromptTuneAuditSamplesService samplesStore;
    private final KnowledgeBaseBuildRunsService buildRunsStore;
    private final KnowledgeBasesService knowledgeBasesService;
    private final AuditPipelineOrchestrator orchestrator;
    private final BuildRunWorkspaceService workspaceService;
    private final AuditSamplePersistenceService persistence;
    private final AuditSampleResponseMapper responseMapper;
    private final ObjectMapper objectMapper;

    /**
     * 重新生成标注样本集。
     *
     * @param buildRunId 构建运行 ID
     * @param force      是否强制覆盖已有标注
     * @return 生成后的样本响应列表
     */
    public List<AuditSampleResponse> regenerateAuditSet(Long buildRunId, boolean force) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);

        if (!force && persistence.hasNonPendingSamples(buildRunId)) {
            throw new BusinessException(ApiResultCode.BUILD_RUN_HAS_ANNOTATED_SAMPLES, HttpStatus.CONFLICT);
        }

        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(buildRun.getKnowledgeBaseId());

        List<Long> materialIds = parseMaterialIds(buildRun.getSelectedMaterialIds());
        if (materialIds.isEmpty()) {
            throw new BusinessException(ApiResultCode.AUDIT_PIPELINE_FAILED, HttpStatus.BAD_REQUEST,
                    "本次构建尚未选择任何资料");
        }

        Path workspaceDir = workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt").resolve("audit");

        AuditPipelineOrchestrator.AuditPipelineResult pipelineResult;
        try {
            pipelineResult = orchestrator.runFullPipeline(knowledgeBase, materialIds, workspaceDir);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ApiResultCode.AUDIT_PIPELINE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR,
                    "标注流水线执行异常: " + e.getMessage());
        }

        List<PromptTuneAuditSamples> persisted = persistence.replaceForBuildRun(buildRun, pipelineResult.getAuditSetFile());
        return assembleResponses(persisted);
    }

    /**
     * 查询指定构建运行的所有标注样本。
     *
     * @param buildRunId 构建运行 ID
     * @return 样本响应列表
     */
    public List<AuditSampleResponse> listSamples(Long buildRunId) {
        buildRunsStore.getRequiredById(buildRunId); // 验证存在性
        List<PromptTuneAuditSamples> rows = samplesStore.listByBuildRunId(buildRunId);
        return assembleResponses(rows);
    }

    /**
     * 更新单条标注样本（三态 PATCH 语义）。
     *
     * @param buildRunId 构建运行 ID
     * @param sampleId   样本 ID
     * @param request    更新请求
     * @return 更新后的样本响应
     */
    public AuditSampleResponse updateSample(Long buildRunId, Long sampleId, AuditSampleUpdateRequest request) {
        PromptTuneAuditSamples sample = samplesStore.getById(sampleId);
        if (sample == null || !Objects.equals(sample.getBuildRunId(), buildRunId)) {
            throw new BusinessException(ApiResultCode.AUDIT_SAMPLE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        // 三态 PATCH：hasField=true 时更新（null=清空，value=设置），hasField=false 时保持不变
        if (request.hasField("reviewerDecision")) {
            String decision = request.getReviewerDecision();
            sample.setReviewerDecision(decision == null ? "pending" : decision);
        }

        if (request.hasField("reviewerConfidence")) {
            sample.setReviewerConfidence(request.getReviewerConfidence());
        }

        if (request.hasField("skipReason")) {
            sample.setSkipReason(request.getSkipReason());
        }

        if (request.hasField("annotationNotes")) {
            sample.setAnnotationNotes(request.getAnnotationNotes());
        }

        if (request.hasField("goldEntities")) {
            sample.setGoldEntities(serializeJson(request.getGoldEntities()));
        }

        if (request.hasField("goldRelations")) {
            sample.setGoldRelations(serializeJson(request.getGoldRelations()));
        }

        if (request.hasField("aiSuggestedEntities")) {
            sample.setAiSuggestedEntities(serializeJson(request.getAiSuggestedEntities()));
        }

        if (request.hasField("aiSuggestedRelations")) {
            sample.setAiSuggestedRelations(serializeJson(request.getAiSuggestedRelations()));
        }

        sample.setUpdatedAt(LocalDateTime.now());
        samplesStore.updateById(sample);

        return assembleResponse(sample);
    }

    // ─── 私有辅助方法 ───────────────────────────────────────────────────────

    private List<AuditSampleResponse> assembleResponses(List<PromptTuneAuditSamples> samples) {
        // 收集需要查询展示名的 reusedFromBuildRunId
        Set<Long> reusedBuildRunIds = samples.stream()
                .map(PromptTuneAuditSamples::getReusedFromBuildRunId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> nameById = new HashMap<>();
        if (!reusedBuildRunIds.isEmpty()) {
            List<KnowledgeBaseBuildRuns> reusedRuns = buildRunsStore.listByIds(reusedBuildRunIds);
            nameById = reusedRuns.stream()
                    .collect(Collectors.toMap(KnowledgeBaseBuildRuns::getId,
                            this::formatBuildRunDisplayName,
                            (left, right) -> left));
        }

        Map<Long, String> finalNameById = nameById;
        return samples.stream()
                .map(row -> responseMapper.toResponse(row, finalNameById.get(row.getReusedFromBuildRunId())))
                .collect(Collectors.toList());
    }

    private AuditSampleResponse assembleResponse(PromptTuneAuditSamples sample) {
        return assembleResponses(List.of(sample)).get(0);
    }

    private List<Long> parseMaterialIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.warn("解析 selectedMaterialIds 失败，降级为空列表。原始值: {}", json, e);
            return List.of();
        }
    }

    private String serializeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("序列化 JSON 失败，降级为 []", e);
            return "[]";
        }
    }

    private String formatBuildRunDisplayName(KnowledgeBaseBuildRuns buildRun) {
        if (buildRun.getBuildVersion() != null && !buildRun.getBuildVersion().isBlank()) {
            return buildRun.getBuildVersion();
        }
        String suffix = buildRun.getCreatedAt() != null ? " · " + buildRun.getCreatedAt() : "";
        return "构建 #" + buildRun.getId() + suffix;
    }
}
