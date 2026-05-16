package org.ysu.ckqaback.index.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PUT /audit-samples/{sampleId} 请求体。
 *
 * <h3>三态 PATCH 语义</h3>
 * <ul>
 *   <li>字段未出现在请求 JSON → 不更新（{@link #hasField(String)} 返回 false）；</li>
 *   <li>字段值为 {@code null} → 显式清空（hasField=true，值为 null）；</li>
 *   <li>字段值非空 → 更新为该值。</li>
 * </ul>
 *
 * <p>实现方式：已知字段全部手写 setter，每个 setter 在写入业务字段的同时把字段名记到
 * {@link #presentFields}；未知字段由 {@link JsonAnySetter} 兜底（仅记录字段名，
 * 业务上忽略）。这样 Jackson 反序列化能严格区分"缺字段"与"显式 null"两种态。</p>
 */
@Getter
public class AuditSampleUpdateRequest {

    private static final Set<String> KNOWN_FIELDS = Set.of(
            "goldEntities",
            "goldRelations",
            "aiSuggestedEntities",
            "aiSuggestedRelations",
            "annotationNotes",
            "reviewerDecision",
            "reviewerConfidence",
            "skipReason"
    );

    private List<Map<String, Object>> goldEntities;
    private List<Map<String, Object>> goldRelations;
    /**
     * AI 候选实体列表（PATCH 字段）。前端在用户接受/拒绝 AI 候选时增量同步：
     * 把当前剩余候选数组写入此字段，覆盖 DB 旧值。
     */
    private List<Map<String, Object>> aiSuggestedEntities;
    /** AI 候选关系列表（语义同 {@link #aiSuggestedEntities}）。 */
    private List<Map<String, Object>> aiSuggestedRelations;
    private String annotationNotes;

    /** pending / in_progress / completed / skipped。null 时由 service 视为"清空决策"。 */
    @Pattern(
            regexp = "^(pending|in_progress|completed|skipped)$",
            message = "reviewerDecision 仅允许 pending / in_progress / completed / skipped"
    )
    private String reviewerDecision;

    @DecimalMin(value = "0.00", message = "置信度不得低于 0")
    @DecimalMax(value = "1.00", message = "置信度不得高于 1")
    private BigDecimal reviewerConfidence;

    private String skipReason;

    /** Jackson 反序列化期间记录"被传入的字段名"集合，用于三态判定。 */
    @JsonIgnore
    private final Set<String> presentFields = new HashSet<>();

    /**
     * Jackson 在遇到未知字段时回调；本类用它仅记录字段名，业务字段一概忽略。
     * 已知字段不会触发此方法，由下面手写的 setter 直接处理。
     */
    @JsonAnySetter
    public void recordUnknownField(String name, Object value) {
        presentFields.add(name);
    }

    // ---------- 已知字段的手写 setter：负责写入业务字段 + 记录字段出现 ----------

    public void setGoldEntities(List<Map<String, Object>> value) {
        this.goldEntities = value;
        presentFields.add("goldEntities");
    }

    public void setGoldRelations(List<Map<String, Object>> value) {
        this.goldRelations = value;
        presentFields.add("goldRelations");
    }

    public void setAiSuggestedEntities(List<Map<String, Object>> value) {
        this.aiSuggestedEntities = value;
        presentFields.add("aiSuggestedEntities");
    }

    public void setAiSuggestedRelations(List<Map<String, Object>> value) {
        this.aiSuggestedRelations = value;
        presentFields.add("aiSuggestedRelations");
    }

    public void setAnnotationNotes(String value) {
        this.annotationNotes = value;
        presentFields.add("annotationNotes");
    }

    public void setReviewerDecision(String value) {
        this.reviewerDecision = value;
        presentFields.add("reviewerDecision");
    }

    public void setReviewerConfidence(BigDecimal value) {
        this.reviewerConfidence = value;
        presentFields.add("reviewerConfidence");
    }

    public void setSkipReason(String value) {
        this.skipReason = value;
        presentFields.add("skipReason");
    }

    public boolean hasField(String name) {
        return presentFields.contains(name) && KNOWN_FIELDS.contains(name);
    }
}
