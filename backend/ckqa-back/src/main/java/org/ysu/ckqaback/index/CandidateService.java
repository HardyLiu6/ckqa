package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.CandidateResponse;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 03 步候选 prompt 业务编排。
 *
 * <p>三个核心方法：</p>
 * <ul>
 *   <li>{@link #generate(Long)}：导出 DB gold → 调脚本 → 读 manifest → 返回候选列表</li>
 *   <li>{@link #list(Long)}：纯只读，从 manifest 读取；不存在抛 4105</li>
 *   <li>{@link #loadPromptText(Long, String)}：抽屉懒加载 prompt 全文</li>
 * </ul>
 *
 * <p>不使用 {@code @Transactional}：本服务唯一的“写”操作是文件系统（audit_with_gold.json
 * 和候选目录），不修改业务表。</p>
 */
@Service
@RequiredArgsConstructor
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    private final KnowledgeBaseBuildRunsService buildRunsStore;
    private final PromptTuneAuditSamplesService samplesStore;
    private final BuildRunWorkspaceService workspaceService;
    private final CandidateGenerationOrchestrator orchestrator;
    private final AuditWithGoldExporter auditExporter;
    private final CandidateManifestReader manifestReader;
    private final CandidateMetadataLookup metadataLookup;
    private final ObjectMapper objectMapper;

    /**
     * 同步生成候选 prompt（覆盖式）：
     * <ol>
     *   <li>过滤已 completed 的样本（4104 后端门控）</li>
     *   <li>导出含 DB gold 的 audit JSON（仅 completed 样本作为 fewshot 来源）</li>
     *   <li>调 Python 脚本生成候选</li>
     *   <li>读 manifest 校验所有候选 id ∈ 白名单（契约漂移检测）</li>
     *   <li>返回候选列表</li>
     * </ol>
     */
    public List<CandidateResponse> generate(Long buildRunId) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);

        // 1. 后端门控：过滤已 completed 的样本，0 条时拒绝生成
        //    审阅意见 #1 + 决策 7：前端门控不能替代后端门控；未完成样本里的 gold 是中间态，
        //    不能作为可信 fewshot 训练材料（避免污染 distilled 候选）。
        //    在解析 workspace 路径之前先做门控，避免无意义的副作用。
        List<PromptTuneAuditSamples> allSamples = samplesStore.listByBuildRunId(buildRunId);
        List<PromptTuneAuditSamples> completedSamples = allSamples.stream()
                .filter(s -> "completed".equals(s.getReviewerDecision()))
                .toList();
        if (completedSamples.isEmpty()) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_REQUIRES_AUDIT_COMPLETED,
                    HttpStatus.BAD_REQUEST
            );
        }

        Path candidatesDir = candidatesDirOf(buildRun);

        Path auditWithGoldFile = candidatesDir.resolve("audit_with_gold.json");
        try {
            Files.createDirectories(candidatesDir);
            // 关键：只把 completed 样本传给 exporter，避免 in_progress 样本的中间态 gold 污染 fewshot
            auditExporter.export(completedSamples, auditWithGoldFile);
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "导出 audit_with_gold.json 失败: " + e.getMessage()
            );
        }

        try {
            orchestrator.run(auditWithGoldFile, candidatesDir, null);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "候选 Prompt 生成异常: " + e.getMessage()
            );
        }

        // 2. 读 manifest。注意：reader 已在白名单外的候选跳过，但这里做的是“反向校验”：
        //    确认脚本输出的候选**都**在白名单内。若 manifest 文件中出现未知候选，说明脚本契约漂移。
        //    由于 reader 已经跳过未知候选，这里通过比较“读出数量 vs manifest 原始数量”间接探测。
        try {
            // 读原 manifest 拿原始候选 id 列表
            Path manifestFile = candidatesDir.resolve("manifest.json");
            int rawCandidateCount = 0;
            if (Files.exists(manifestFile)) {
                Map<String, Object> root = objectMapper.readValue(
                        Files.readString(manifestFile), new TypeReference<>() {});
                Object cand = root.get("candidates");
                if (cand instanceof List<?> list) rawCandidateCount = list.size();
            }
            List<CandidateResponse> candidates = manifestReader.read(candidatesDir);
            if (rawCandidateCount > candidates.size()) {
                throw new BusinessException(
                        ApiResultCode.CANDIDATE_GENERATION_FAILED,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "脚本输出包含未知候选：原始 " + rawCandidateCount + "，识别 " + candidates.size()
                                + "。请检查 CandidateMetadataLookup 是否需要扩容。"
                );
            }
            log.info("候选生成完成 buildRunId={}, count={}, sampleSource=completed×{}",
                    buildRunId, candidates.size(), completedSamples.size());
            return candidates;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取候选 manifest 失败: " + e.getMessage()
            );
        }
    }

    /**
     * 纯只读：从 build run workspace 下的 manifest.json 读出候选列表。
     * Manifest 不存在或为空时抛 4105（HTTP 404 + body 含 code:4105 + data:null），
     * 让前端引导用户调 generate。
     */
    public List<CandidateResponse> list(Long buildRunId) {
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);
        Path candidatesDir = candidatesDirOf(buildRun);
        try {
            List<CandidateResponse> candidates = manifestReader.read(candidatesDir);
            if (candidates.isEmpty()) {
                throw new BusinessException(
                        ApiResultCode.CANDIDATES_NOT_GENERATED,
                        HttpStatus.NOT_FOUND
                );
            }
            return candidates;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取候选 manifest 失败: " + e.getMessage()
            );
        }
    }

    /**
     * 抽屉懒加载：读 prompt.txt 全文。candidateId 必须 ∈ 白名单（防路径穿越）。
     */
    public String loadPromptText(Long buildRunId, String candidateId) {
        if (!metadataLookup.isKnown(candidateId)) {
            throw new BusinessException(
                    ApiResultCode.BAD_REQUEST,
                    HttpStatus.BAD_REQUEST,
                    "未知的候选标识：" + candidateId
            );
        }
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);
        Path promptFile = candidatesDirOf(buildRun)
                .resolve(candidateId)
                .resolve("prompt.txt");
        if (!Files.exists(promptFile)) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATES_NOT_GENERATED,
                    HttpStatus.NOT_FOUND,
                    "候选 prompt 文件不存在：" + candidateId
            );
        }
        try {
            return Files.readString(promptFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(
                    ApiResultCode.CANDIDATE_GENERATION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "读取 prompt 文件失败: " + e.getMessage()
            );
        }
    }

    private Path candidatesDirOf(KnowledgeBaseBuildRuns buildRun) {
        return workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt")
                .resolve("candidates");
    }
}
