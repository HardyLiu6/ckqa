package org.ysu.ckqaback.index;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBaseBuildRuns;
import org.ysu.ckqaback.entity.PromptTuneAuditSamples;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.service.PromptTuneAuditSamplesService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 标注样本持久化服务。
 * <p>
 * 负责 audit 样本的 JSON 解析、数据库写入、历史标注合并等操作。
 * 仅 {@link #replaceForBuildRun} 方法使用事务边界。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AuditSamplePersistenceService {

    private static final Set<String> VALID_PRIORITIES = Set.of("high", "medium", "low");

    private final PromptTuneAuditSamplesService samplesStore;
    private final ObjectMapper objectMapper;

    /**
     * 解析 audit JSON 文件，替换指定 buildRun 的所有样本数据。
     *
     * @param buildRun 当前构建运行
     * @param auditSetFile audit 样本 JSON 文件路径
     * @return 保存后的完整样本列表
     */
    @Transactional
    public List<PromptTuneAuditSamples> replaceForBuildRun(KnowledgeBaseBuildRuns buildRun, Path auditSetFile) {
        List<Map<String, Object>> auditArray = parseAuditJson(auditSetFile);

        // 删除该 buildRun 的旧样本
        LambdaQueryWrapper<PromptTuneAuditSamples> removeWrapper = new LambdaQueryWrapper<>();
        removeWrapper.eq(PromptTuneAuditSamples::getBuildRunId, buildRun.getId());
        samplesStore.remove(removeWrapper);

        // 构建新实体列表
        List<PromptTuneAuditSamples> newRows = new ArrayList<>();
        for (Map<String, Object> item : auditArray) {
            newRows.add(buildEntityFromAuditJson(item, buildRun));
        }

        // 合并历史标注
        mergeReusedAnnotations(buildRun.getKnowledgeBaseId(), buildRun.getId(), newRows);

        // 批量保存
        samplesStore.saveBatch(newRows);

        return samplesStore.listByBuildRunId(buildRun.getId());
    }

    /**
     * 合并历史已完成标注到当前样本。
     * <p>
     * 通过 goldStableKey 查找同一知识库下已完成的历史标注，
     * 选取最近更新的记录（排除同一 buildRunId），复制 gold 字段。
     * </p>
     */
    public void mergeReusedAnnotations(Long knowledgeBaseId, Long currentBuildRunId,
                                       List<PromptTuneAuditSamples> samples) {
        // 收集有效的 stableKey
        List<String> stableKeys = samples.stream()
                .map(PromptTuneAuditSamples::getGoldStableKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (stableKeys.isEmpty()) {
            return;
        }

        // 查询历史已完成标注
        List<PromptTuneAuditSamples> historicalSamples =
                samplesStore.findCompletedByStableKeys(knowledgeBaseId, stableKeys);

        // 构建 stableKey → 最佳历史样本映射（排除同一 buildRunId，取最近 updatedAt）
        Map<String, PromptTuneAuditSamples> bestByKey = new HashMap<>();
        for (PromptTuneAuditSamples hist : historicalSamples) {
            if (Objects.equals(hist.getBuildRunId(), currentBuildRunId)) {
                continue;
            }
            String key = hist.getGoldStableKey();
            PromptTuneAuditSamples existing = bestByKey.get(key);
            if (existing == null || isMoreRecent(hist, existing)) {
                bestByKey.put(key, hist);
            }
        }

        // 复制 gold 字段到匹配的当前样本
        for (PromptTuneAuditSamples sample : samples) {
            String key = sample.getGoldStableKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            PromptTuneAuditSamples best = bestByKey.get(key);
            if (best != null) {
                sample.setGoldEntities(best.getGoldEntities());
                sample.setGoldRelations(best.getGoldRelations());
                sample.setAnnotationNotes(best.getAnnotationNotes());
                sample.setReviewerDecision(best.getReviewerDecision());
                sample.setReviewerConfidence(best.getReviewerConfidence());
                sample.setReusedFromBuildRunId(best.getBuildRunId());
            }
        }
    }

    /**
     * 判断指定 buildRun 是否存在非 pending 状态的样本。
     */
    public boolean hasNonPendingSamples(Long buildRunId) {
        LambdaQueryWrapper<PromptTuneAuditSamples> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTuneAuditSamples::getBuildRunId, buildRunId)
                .ne(PromptTuneAuditSamples::getReviewerDecision, "pending");
        return samplesStore.count(wrapper) > 0;
    }

    // ─── 私有方法 ─────────────────────────────────────────────────────────────

    private List<Map<String, Object>> parseAuditJson(Path auditSetFile) {
        try {
            String content = Files.readString(auditSetFile);
            Map<String, Object> root = objectMapper.readValue(content,
                    new TypeReference<Map<String, Object>>() {});
            Object samplesObj = root.get("audit_samples");
            if (samplesObj instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> result = (List<Map<String, Object>>) list;
                return result;
            }
            throw new BusinessException(ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "audit JSON 缺少 audit_samples 数组");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ApiResultCode.AUDIT_PIPELINE_FAILED,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "解析 audit JSON 失败: " + e.getMessage());
        }
    }

    private PromptTuneAuditSamples buildEntityFromAuditJson(Map<String, Object> item,
                                                            KnowledgeBaseBuildRuns buildRun) {
        PromptTuneAuditSamples entity = new PromptTuneAuditSamples();
        LocalDateTime now = LocalDateTime.now();

        entity.setBuildRunId(buildRun.getId());
        entity.setKnowledgeBaseId(buildRun.getKnowledgeBaseId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        entity.setSourceSampleId(getString(item, "source_sample_id"));
        entity.setText(getString(item, "text"));
        entity.setHeadingPath(resolveHeadingPath(item.get("heading_path")));
        entity.setPageStart(getInteger(item, "page_start"));
        entity.setPageEnd(getInteger(item, "page_end"));
        entity.setDocumentType(getString(item, "document_type"));
        entity.setAuditPriority(coerceAuditPriority(getString(item, "audit_priority")));
        entity.setAuditReason(getString(item, "audit_reason"));
        entity.setHitSignals(serializeToJson(item.get("hit_signals")));
        entity.setGoldEntities(serializeToJsonOrDefault(item.get("gold_entities"), "[]"));
        entity.setGoldRelations(serializeToJsonOrDefault(item.get("gold_relations"), "[]"));
        entity.setAnnotationNotes(getString(item, "annotation_notes"));
        entity.setReviewerDecision(coerceReviewerDecision(getString(item, "reviewer_decision")));
        entity.setReviewerConfidence(parseBigDecimal(item.get("reviewer_confidence")));
        entity.setGoldStableKey(getString(item, "gold_stable_key"));

        return entity;
    }

    private String resolveHeadingPath(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(" > "));
        }
        if (value instanceof String s) {
            return s;
        }
        return null;
    }

    private String coerceAuditPriority(String value) {
        if (value != null && VALID_PRIORITIES.contains(value.toLowerCase())) {
            return value.toLowerCase();
        }
        return "medium";
    }

    private String coerceReviewerDecision(String value) {
        if (value == null || value.isBlank()) {
            return "pending";
        }
        return value;
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String serializeToJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private String serializeToJsonOrDefault(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            String result = objectMapper.writeValueAsString(value);
            return result;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getInteger(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private boolean isMoreRecent(PromptTuneAuditSamples candidate, PromptTuneAuditSamples current) {
        LocalDateTime candidateTime = candidate.getUpdatedAt();
        LocalDateTime currentTime = current.getUpdatedAt();
        if (candidateTime == null) return false;
        if (currentTime == null) return true;
        return candidateTime.isAfter(currentTime);
    }
}
