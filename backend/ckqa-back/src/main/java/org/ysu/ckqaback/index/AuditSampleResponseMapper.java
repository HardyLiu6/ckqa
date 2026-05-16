package org.ysu.ckqaback.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.index.dto.AuditSampleResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link PromptTuneAuditSamples} 实体转换为 {@link AuditSampleResponse} DTO。
 * <p>
 * 负责解析实体中以 JSON 字符串存储的字段（gold_entities、gold_relations、hit_signals），
 * 并在 JSON 格式异常时安全降级为空列表。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditSampleResponseMapper {

    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> LIST_STRING_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    /**
     * 将实体转换为响应 DTO。
     *
     * @param entity             审计样本实体
     * @param reusedBuildRunName 复用来源构建的展示名（仅当 entity.reusedFromBuildRunId 非空时使用）
     * @return 构建好的响应 DTO
     */
    public AuditSampleResponse toResponse(PromptTuneAuditSamples entity, String reusedBuildRunName) {
        return AuditSampleResponse.builder()
                .id(entity.getId())
                .buildRunId(entity.getBuildRunId())
                .sourceSampleId(entity.getSourceSampleId())
                .text(entity.getText())
                .headingPath(entity.getHeadingPath())
                .pageStart(entity.getPageStart())
                .pageEnd(entity.getPageEnd())
                .documentType(entity.getDocumentType())
                .auditPriority(entity.getAuditPriority())
                .auditReason(entity.getAuditReason())
                .hitSignals(parseStringList(entity.getHitSignals()))
                .goldEntities(parseMapList(entity.getGoldEntities()))
                .goldRelations(parseMapList(entity.getGoldRelations()))
                .aiSuggestedEntities(parseMapList(entity.getAiSuggestedEntities()))
                .aiSuggestedRelations(parseMapList(entity.getAiSuggestedRelations()))
                .annotationNotes(entity.getAnnotationNotes())
                .reviewerDecision(entity.getReviewerDecision())
                .reviewerConfidence(entity.getReviewerConfidence())
                .skipReason(entity.getSkipReason())
                .goldStableKey(entity.getGoldStableKey())
                .reusedFrom(buildReusedFrom(entity, reusedBuildRunName))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AuditSampleResponse.ReusedFromInfo buildReusedFrom(PromptTuneAuditSamples entity, String reusedBuildRunName) {
        if (entity.getReusedFromBuildRunId() == null) {
            return null;
        }
        return AuditSampleResponse.ReusedFromInfo.builder()
                .buildRunId(entity.getReusedFromBuildRunId())
                .buildRunName(reusedBuildRunName)
                .reusedAt(entity.getCreatedAt())
                .build();
    }

    private List<Map<String, Object>> parseMapList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> result = objectMapper.readValue(json, LIST_MAP_TYPE);
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.warn("JSON 数组解析失败，降级为空列表。原始值: {}", json, e);
            return Collections.emptyList();
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> result = objectMapper.readValue(json, LIST_STRING_TYPE);
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.warn("JSON 字符串数组解析失败，降级为空列表。原始值: {}", json, e);
            return Collections.emptyList();
        }
    }
}
