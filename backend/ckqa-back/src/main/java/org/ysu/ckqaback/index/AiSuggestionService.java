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
 * 不使用 {@code @Transactional}：本服务唯一的"写"操作是日志/中间文件，
 * 不直接修改业务表。AI 候选不持久化到 DB（属于待审状态，被采纳后才进 goldEntities）。
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

        return AiSuggestionResponse.builder()
                .entities(markEntitiesAsAiSuggested(result.getEntities()))
                .relations(markRelationsAsAiSuggested(result.getRelations()))
                .build();
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
     * 决定使用哪个 prompt 文件：本期固定使用 GraphRAG 默认 {@code prompts/extract_graph.txt}。
     * <p>
     * Build run 的 customPromptDraft 暂未读取——它当前以 JSON 字符串形式存在 build_metadata 中，
     * 把它落到临时文件的逻辑随 Phase 6 一起做。
     * </p>
     */
    private Path resolvePromptFile(KnowledgeBaseBuildRuns buildRun) {
        return Path.of(properties.getGraphrag().getRoot()).resolve("prompts").resolve("extract_graph.txt");
    }

    private List<Map<String, Object>> markEntitiesAsAiSuggested(List<Map<String, Object>> entities) {
        return entities.stream()
                .map(this::normalizeEntityType)
                .map(e -> {
                    Map<String, Object> copy = new LinkedHashMap<>(e);
                    copy.put("suggestionSource", "ai_suggested");
                    return copy;
                })
                .toList();
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
        return relations.stream()
                .map(r -> {
                    Map<String, Object> copy = new LinkedHashMap<>(r);
                    Object originalSource = copy.remove("source");
                    Object originalTarget = copy.remove("target");
                    if (originalSource != null) copy.put("originalSource", originalSource);
                    if (originalTarget != null) copy.put("originalTarget", originalTarget);
                    copy.put("suggestionSource", "ai_suggested");
                    return copy;
                })
                .toList();
    }
}
