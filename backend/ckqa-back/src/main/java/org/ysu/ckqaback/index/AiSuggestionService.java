package org.ysu.ckqaback.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.index.dto.AiSuggestionResponse;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.KnowledgeBaseBuildRunsService;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 预填业务编排（Phase 3）。
 * <p>
 * 不使用 {@code @Transactional}：本服务的写操作只有"覆盖 sample 的 ai_suggested_*
 * 两个 JSON 字段"，原子的单条 update 即可，不需要事务边界。
 * </p>
 * <p>
 * AI 候选持久化策略（v2，Phase 3 修订）：
 * 抽取完成后把候选 JSON 直接写到 {@link PromptTuneAuditSamples#aiSuggestedEntities}
 * 和 {@link PromptTuneAuditSamples#aiSuggestedRelations}，避免页面刷新或切换样本后
 * 丢失（单次抽取 ~130 秒、~21k tokens）。用户在前端接受/拒绝候选时，会通过
 * PUT /audit-samples/{sampleId} 把剩余候选数组覆盖回这两个字段。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AiSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiSuggestionService.class);

    /** 与前端 schema hardcode 同步的 11 种实体类型，作为 GraphRAG 抽取目标。 */
    private static final String DEFAULT_ENTITY_TYPES =
            "Course,Chapter,Section,KnowledgePoint,Concept,Term,FormulaOrDefinition,"
            + "AlgorithmOrMethod,Experiment,Assignment,ToolOrPlatform";

    /**
     * 11 种实体类型的"无空格大写 → canonical"查表，用于规范化 LLM 输出。
     * <p>
     * LLM 实际输出可能带空格（如 "KNOWLEDGE POINT"）或全大写（"CONCEPT"），
     * Python 端 {@code _canonicalize_entity_type} 只对全大写无空格做映射，
     * 不处理带空格变体。这里在 service 层做更宽容的匹配，让"同义变体"都能落到 schema 内。
     * </p>
     */
    private static final Map<String, String> ENTITY_TYPE_LOOKUP = buildEntityTypeLookup();

    /** schema 外的兜底类型（schema 内最通用、最常用的类型）。 */
    private static final String FALLBACK_ENTITY_TYPE = "Concept";

    /** 候选标识符常量，与 CandidateMetadataLookup / CandidateService 保持一致。 */
    private static final String DEFAULT_CANDIDATE_ID = "schema_fewshot_distilled_v2_strict_tuple";

    private static Map<String, String> buildEntityTypeLookup() {
        Map<String, String> lookup = new LinkedHashMap<>();
        for (String t : DEFAULT_ENTITY_TYPES.split(",")) {
            String canonical = t.trim();
            if (canonical.isEmpty()) continue;
            lookup.put(normalizeTypeKey(canonical), canonical);
        }
        return Map.copyOf(lookup);
    }

    /** 把一个 type 字符串规范化为查表 key：去空格 + 全大写。 */
    private static String normalizeTypeKey(String raw) {
        return raw.replaceAll("\\s+", "").toUpperCase();
    }

    private final PromptTuneAuditSamplesService samplesStore;
    private final KnowledgeBaseBuildRunsService buildRunsStore;
    private final SingleSampleExtractionOrchestrator orchestrator;
    private final BuildRunWorkspaceService workspaceService;
    private final CkqaIntegrationProperties properties;
    private final ObjectMapper objectMapper;

    public AiSuggestionResponse generate(Long buildRunId, Long sampleId) {
        PromptTuneAuditSamples sample = samplesStore.getById(sampleId);
        if (sample == null || !Objects.equals(sample.getBuildRunId(), buildRunId)) {
            throw new BusinessException(ApiResultCode.AUDIT_SAMPLE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        KnowledgeBaseBuildRuns buildRun = buildRunsStore.getRequiredById(buildRunId);

        Map<String, Object> sampleJson = sampleToGraphRagInput(sample);
        Path workspaceDir = workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt").resolve("ai-suggestion").resolve("sample-" + sampleId);

        Path promptFile = resolvePromptFile(buildRun);

        // 确定性 run id：buildRunId + sampleId + 时间戳，避免不同请求互相覆盖输出文件
        String runId = "ai_suggestion_" + buildRunId + "_" + sampleId + "_" + System.currentTimeMillis();

        SingleSampleExtractionOrchestrator.ExtractionResult result;
        try {
            result = orchestrator.runSingleExtract(
                    sampleJson, DEFAULT_ENTITY_TYPES, promptFile, workspaceDir, runId
            );
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(
                    ApiResultCode.AI_SUGGESTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 候选生成异常: " + e.getMessage()
            );
        }

        // 标记 + 规范化候选数据，并分配稳定 id（基于 sampleId + 索引），
        // 让前端在采纳/拒绝/刷新场景下能始终通过 id 定位候选条目
        List<Map<String, Object>> markedEntities =
                markEntitiesAsAiSuggested(result.getEntities(), sampleId);
        List<Map<String, Object>> markedRelations =
                markRelationsAsAiSuggested(result.getRelations(), sampleId);

        // 持久化到 sample.ai_suggested_*：刷新页面、切样本后还能看到，
        // 不需要重新花 ~130 秒 + ~21k tokens 重跑
        persistSuggestionsToSample(sample, markedEntities, markedRelations);

        return AiSuggestionResponse.builder()
                .entities(markedEntities)
                .relations(markedRelations)
                .build();
    }

    /**
     * 把候选 JSON 写回 sample 的 ai_suggested_* 字段。
     * <p>
     * 单条 update，无事务，失败抛 BusinessException。本期"重新生成"语义就是覆盖：
     * 后端不区分"清空 + 写新"，直接全量覆盖即可。
     * </p>
     */
    private void persistSuggestionsToSample(
            PromptTuneAuditSamples sample,
            List<Map<String, Object>> entities,
            List<Map<String, Object>> relations
    ) {
        try {
            sample.setAiSuggestedEntities(objectMapper.writeValueAsString(entities));
            sample.setAiSuggestedRelations(objectMapper.writeValueAsString(relations));
            samplesStore.updateById(sample);
        } catch (Exception e) {
            log.error("AI 候选持久化失败 sampleId={}", sample.getId(), e);
            throw new BusinessException(
                    ApiResultCode.AI_SUGGESTION_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "AI 候选持久化失败: " + e.getMessage()
            );
        }
    }

    /**
     * 把 PromptTuneAuditSamples 实体的字段重组成 audit_extraction_set.json 中样本的格式。
     * GraphRAG 脚本期望 source_sample_id / text / heading_path / page_start / page_end 等字段。
     */
    private Map<String, Object> sampleToGraphRagInput(PromptTuneAuditSamples sample) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("source_sample_id", sample.getSourceSampleId());
        json.put("text", sample.getText());
        // heading_path 在 DB 中是 " > " 分隔字符串；GraphRAG 期望 List<String>
        if (sample.getHeadingPath() != null && !sample.getHeadingPath().isBlank()) {
            json.put("heading_path", List.of(sample.getHeadingPath().split(" > ")));
        } else {
            json.put("heading_path", List.of());
        }
        json.put("page_start", sample.getPageStart() != null ? sample.getPageStart() : 0);
        json.put("page_end", sample.getPageEnd() != null ? sample.getPageEnd() : 0);
        json.put("audit_priority", sample.getAuditPriority());
        json.put("audit_reason", sample.getAuditReason());
        return json;
    }

    /**
     * 决定使用哪个 prompt 文件：
     * <ol>
     *   <li>build run workspace 下的 candidate（{@code <workspace>/prompt/candidates/{cid}/prompt.txt}）
     *       存在 → 用它（含本次 audit gold 的 fewshot 蒸馏，更准确）</li>
     *   <li>否则 fallback 到仓库根 frozen_v1（{@code <GRAPHRAG_ROOT>/prompts/candidates/{cid}/prompt.txt}）
     *       —— 用户还没跑 03 步时的兼容路径</li>
     * </ol>
     *
     * <p>Phase 6 后改为读 {@code prompts/final/active_prompt.json} 拿到当前激活的 candidate 路径。
     * 详见 spec § Phase 6。</p>
     */
    private Path resolvePromptFile(KnowledgeBaseBuildRuns buildRun) {
        Path buildRunCandidate = workspaceService.resolve(buildRun.getWorkspaceUri())
                .resolve("prompt").resolve("candidates")
                .resolve(DEFAULT_CANDIDATE_ID).resolve("prompt.txt");
        if (java.nio.file.Files.exists(buildRunCandidate)) {
            return buildRunCandidate;
        }
        return Path.of(properties.getGraphrag().getRoot())
                .resolve("prompts").resolve("candidates")
                .resolve(DEFAULT_CANDIDATE_ID).resolve("prompt.txt");
    }

    private List<Map<String, Object>> markEntitiesAsAiSuggested(List<Map<String, Object>> entities) {
        return markEntitiesAsAiSuggested(entities, null);
    }

    /**
     * 给每个候选实体加上 {@code suggestionSource: 'ai_suggested'} 标记并规范化 type；
     * 当 sampleId 非空时，同时分配稳定 id {@code ai_e_<sampleId>_<idx>}，
     * 让前端在采纳/拒绝/刷新场景下能始终通过 id 定位候选条目。
     */
    private List<Map<String, Object>> markEntitiesAsAiSuggested(
            List<Map<String, Object>> entities, Long sampleId
    ) {
        List<Map<String, Object>> result = new java.util.ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            Map<String, Object> normalized = normalizeEntityType(entities.get(i));
            Map<String, Object> copy = new LinkedHashMap<>(normalized);
            copy.put("suggestionSource", "ai_suggested");
            if (sampleId != null) {
                copy.put("id", "ai_e_" + sampleId + "_" + i);
            }
            result.add(copy);
        }
        return List.copyOf(result);
    }

    /**
     * 把 LLM 输出的 entity type 规范化到 schema 内的 11 种之一。
     * <ul>
     *   <li>schema 内（含带空格变体如 "KNOWLEDGE POINT"）：标准化为 canonical 命名（"KnowledgePoint"）</li>
     *   <li>schema 外（如 "OPERATING SYSTEM COMPONENT"）：兜底为 {@value #FALLBACK_ENTITY_TYPE}，
     *       同时塞入 {@code originalType} 与 {@code typeOutOfSchema=true}，前端用于显示警告 badge</li>
     *   <li>type 为空或非字符串：原样透传</li>
     * </ul>
     */
    Map<String, Object> normalizeEntityType(Map<String, Object> entity) {
        Object raw = entity.get("type");
        if (!(raw instanceof String s) || s.isBlank()) {
            return entity;
        }
        String key = normalizeTypeKey(s);
        String canonical = ENTITY_TYPE_LOOKUP.get(key);
        Map<String, Object> copy = new LinkedHashMap<>(entity);
        if (canonical != null) {
            // schema 内：把可能的空格变体统一为 canonical
            copy.put("type", canonical);
        } else {
            // schema 外：兜底 + 标志位 + 保留原值
            copy.put("type", FALLBACK_ENTITY_TYPE);
            copy.put("originalType", s);
            copy.put("typeOutOfSchema", true);
        }
        return copy;
    }

    /**
     * GraphRAG 抽出的 relation 用实体名字符串作为 source/target，
     * 但前端期望 sourceEntityId/targetEntityId 指向已确认实体的 id。
     * <p>
     * AI 候选无法预知前端 id，本期妥协：
     * <ul>
     *   <li>用独立字段 {@code suggestionSource} = {@code "ai_suggested"} 标记候选来源
     *       （<strong>不</strong> 覆盖 GraphRAG 的 source/target——避免污染关系领域字段）</li>
     *   <li>原始 source/target（实体名）改名为 {@code originalSource}/{@code originalTarget}</li>
     *   <li>前端采纳时优先查"已采纳 AI 实体本地映射表"匹配 entity id，
     *       未命中时退化按"同名"匹配 sample.goldEntities</li>
     * </ul>
     * </p>
     */
    private List<Map<String, Object>> markRelationsAsAiSuggested(List<Map<String, Object>> relations) {
        return markRelationsAsAiSuggested(relations, null);
    }

    private List<Map<String, Object>> markRelationsAsAiSuggested(
            List<Map<String, Object>> relations, Long sampleId
    ) {
        List<Map<String, Object>> result = new java.util.ArrayList<>(relations.size());
        for (int i = 0; i < relations.size(); i++) {
            Map<String, Object> r = relations.get(i);
            Map<String, Object> copy = new LinkedHashMap<>(r);
            Object originalSource = copy.remove("source");
            Object originalTarget = copy.remove("target");
            if (originalSource != null) copy.put("originalSource", originalSource);
            if (originalTarget != null) copy.put("originalTarget", originalTarget);
            copy.put("suggestionSource", "ai_suggested");
            if (sampleId != null) {
                copy.put("id", "ai_r_" + sampleId + "_" + i);
            }
            result.add(copy);
        }
        return List.copyOf(result);
    }
}
