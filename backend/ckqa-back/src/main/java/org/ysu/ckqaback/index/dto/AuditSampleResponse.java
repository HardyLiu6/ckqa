package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 02 步标注样本响应。
 */
@Getter
@Builder
public class AuditSampleResponse {

    private final Long id;
    private final Long buildRunId;
    private final String sourceSampleId;
    private final String text;
    private final String headingPath;
    private final Integer pageStart;
    private final Integer pageEnd;
    private final String documentType;

    /** high / medium / low。 */
    private final String auditPriority;
    private final String auditReason;

    /** 命中信号列表，如 ["definition_signal", "formula_signal"]。 */
    private final List<String> hitSignals;

    /** 用户标注的实体列表，元素 schema 见 spec § 02 步。 */
    private final List<Map<String, Object>> goldEntities;

    /** 用户标注的关系列表，元素 schema 见 spec § 02 步。 */
    private final List<Map<String, Object>> goldRelations;

    private final String annotationNotes;

    /** pending / in_progress / completed / skipped。 */
    private final String reviewerDecision;
    private final BigDecimal reviewerConfidence;
    private final String skipReason;
    private final String goldStableKey;

    /** 当本条样本是从历史标注复用而来时填充，否则为 null。 */
    private final ReusedFromInfo reusedFrom;

    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class ReusedFromInfo {
        /** 复用来源构建 ID（即 {@code reused_from_build_run_id} 字段所指向的历史 build run）。 */
        private final Long buildRunId;

        /** 复用来源构建的展示名，便于前端 02 步绿色 ♻ 横幅文案直接渲染。 */
        private final String buildRunName;

        /**
         * 本次复用操作发生的时间（即"该样本被本构建以复用方式落库时的快照时间"），
         * 等价于本条 {@code prompt_tune_audit_samples} 记录的 {@code created_at}，
         * 不是历史原始标注的创建时间。
         */
        private final LocalDateTime reusedAt;
    }
}
