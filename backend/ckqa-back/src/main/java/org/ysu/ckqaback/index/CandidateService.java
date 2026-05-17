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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ysu.ckqaback.index.dto.PromptTuneRunResponse;

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
    private final SeedInfoStore seedInfoStore;
    private final PromptTuneService promptTuneService;

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

        // Phase 4.5：根据 build run 当前 seed 决定底板覆盖
        String seed = resolveSeed(buildRun);
        CandidateGenerationOrchestrator.BaseOverride baseOverride = resolveBaseOverride(seed, buildRun, candidatesDir);

        try {
            orchestrator.run(auditWithGoldFile, candidatesDir, baseOverride);
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

        // Phase 4.5：写 seed-info.json（审计文件 + GET 路径的 seed 来源）
        // 写盘失败仅 warn 不阻断响应：本次 POST 返回的候选会通过 Java 侧 withInjectedSeed
        // 直接注入 seed，前端展示不受影响；下次重新进入 03 步走 GET 路径时，若文件
        // 仍缺失，候选 seed 会回落到 null（与 Phase 4 老 build run 行为一致）。
        try {
            SeedInfoStore.SeedInfo info = SeedInfoStore.SeedInfo.builder()
                    .seed(seed)
                    .autoTunedPromptDir(baseOverride != null ? baseOverride.autoTunedPromptDir().toString() : null)
                    .generatedAt(java.time.OffsetDateTime.now())
                    .buildRunId(buildRunId)
                    .build();
            seedInfoStore.write(candidatesDir, info);
        } catch (IOException e) {
            log.warn("写 seed-info.json 失败 buildRunId={}（响应已直接注入 seed，不阻断）", buildRunId, e);
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
            // Phase 4.5：直接把当前 seed 注入响应，不依赖 seed-info.json 是否落盘成功
            // Phase 5.2：按 seed 过滤冗余基线候选（system_default 排除 auto_tuned，graphrag_tuned 排除 default），
            //            永远只透出 3 个候选给前端。Python 仍生成 4 个 prompt 文件（不动脚本），Java 层做白名单。
            java.util.Set<String> allowed = CandidateSeedFilter.allowedCandidatesForSeed(seed);
            List<CandidateResponse> withSeed = candidates.stream()
                    .filter(c -> allowed.contains(c.getCandidateId()))
                    .map(c -> withInjectedSeed(c, seed))
                    .toList();
            log.info("候选生成完成 buildRunId={}, count={}, seed={}, sampleSource=completed×{}",
                    buildRunId, withSeed.size(), seed, completedSamples.size());
            return withSeed;
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
            // Phase 5.2：按 seed 过滤冗余基线候选——与 generate 路径行为一致。
            // 注意：manifestReader 已在 SeedInfoStore.read 注入了 seed 字段，但这里再读一次 build run metadata
            // 兜底（seed-info.json 写盘失败时仍能拿到正确 seed）。
            String seed = resolveSeed(buildRun);
            java.util.Set<String> allowed = CandidateSeedFilter.allowedCandidatesForSeed(seed);
            return candidates.stream()
                    .filter(c -> allowed.contains(c.getCandidateId()))
                    .toList();
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

    /**
     * 从 build run metadata 读 customPromptDraft.seed；缺失返回 null（按"未选择"处理）。
     */
    private String resolveSeed(KnowledgeBaseBuildRuns buildRun) {
        String metadata = buildRun.getBuildMetadata();
        if (metadata == null || metadata.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode seed = root.path("customPromptDraft").path("seed");
            if (seed.isTextual() && !seed.asText().isBlank()) return seed.asText();
        } catch (Exception e) {
            log.warn("解析 build run metadata 失败 buildRunId={}", buildRun.getId(), e);
        }
        return null;
    }

    /**
     * 根据 seed 决定 baseOverride：
     * <ul>
     *   <li>{@code system_default} → 强制 fallback 到 default 分支</li>
     *   <li>{@code graphrag_tuned} → 指向 prompt-tune cache 命中目录;缺失抛 4109</li>
     *   <li>{@code null} 或其它（包括 history_draft，phase 6 范畴） → 返回 null，让 orchestrator 走 Phase 4 兼容路径</li>
     * </ul>
     */
    private CandidateGenerationOrchestrator.BaseOverride resolveBaseOverride(
            String seed,
            KnowledgeBaseBuildRuns buildRun,
            Path candidatesDir
    ) {
        if (seed == null) return null;

        if ("system_default".equals(seed)) {
            return CandidateGenerationOrchestrator.BaseOverride.systemDefault(candidatesDir);
        }

        if ("graphrag_tuned".equals(seed)) {
            // 单一口径：用 probeBySelection 与 SeedAvailabilityService 共享判定逻辑，
            // 确认 cache 当前确实是 success 状态。
            List<Long> materialIds = parseMaterialIds(buildRun.getSelectedMaterialIds());
            PromptTuneRunResponse probe = promptTuneService.probeBySelection(
                    buildRun.getKnowledgeBaseId(), buildRun.getCourseId(), materialIds
            );
            if (!"success".equals(probe.getStatus())) {
                throw new BusinessException(
                        ApiResultCode.SEED_AUTO_TUNED_UNAVAILABLE,
                        HttpStatus.BAD_REQUEST,
                        "graphrag_tuned 种子的自动调优产物当前不可用（status=" + probe.getStatus()
                                + "），请回知识库构建向导触发自动调优后再试"
                );
            }
            return promptTuneService.findReadyByCacheKey(probe.getCacheKey())
                    .map(run -> workspaceService.resolve(run.getCandidateDir()))
                    .map(CandidateGenerationOrchestrator.BaseOverride::graphragTuned)
                    .orElseThrow(() -> new BusinessException(
                            ApiResultCode.SEED_AUTO_TUNED_UNAVAILABLE,
                            HttpStatus.BAD_REQUEST,
                            "graphrag_tuned 自动调优产物 cache 状态与目录不一致，请重新选择种子"
                    ));
        }

        // history_draft 或未知种子：本期当作"无 override"，由 Phase 6 重新落地
        return null;
    }

    private List<Long> parseMaterialIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Phase 4.5：把 seed 注入候选响应。
     * <p>POST 生成路径始终以本次计算出的 seed 为准，覆盖 reader 可能从旧 seed-info.json
     * 带回的过期值。即使 reader 此时返回 null（seed-info.json 还没写或写失败），本方法
     * 也能保证响应里的每个候选都带上正确 seed。</p>
     */
    private static CandidateResponse withInjectedSeed(CandidateResponse src, String seed) {
        return CandidateResponse.builder()
                .candidateId(src.getCandidateId())
                .displayNameZh(src.getDisplayNameZh())
                .category(src.getCategory())
                .description(src.getDescription())
                .isRecommended(src.getIsRecommended())
                .traits(src.getTraits())
                .estimatedTokenPerCall(src.getEstimatedTokenPerCall())
                .promptSizeBytes(src.getPromptSizeBytes())
                .schemaUsed(src.getSchemaUsed())
                .fewshotExampleCount(src.getFewshotExampleCount())
                .fewshotStrategy(src.getFewshotStrategy())
                .basePromptSource(src.getBasePromptSource())
                .generationTime(src.getGenerationTime())
                .seed(seed)
                .build();
    }
}
