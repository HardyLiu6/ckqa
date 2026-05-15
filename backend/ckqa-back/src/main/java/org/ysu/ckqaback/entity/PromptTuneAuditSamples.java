package org.ysu.ckqaback.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 手动调优标注样本表。
 * <p>
 * 存储 02 步标注 IDE 中每条 audit 样本的 gold 标注数据。
 * 通过 {@link #goldStableKey} 支持跨构建复用已有标注。
 * </p>
 */
@Getter
@Setter
@ToString
@TableName("prompt_tune_audit_samples")
public class PromptTuneAuditSamples implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("build_run_id")
    private Long buildRunId;

    @TableField("knowledge_base_id")
    private Long knowledgeBaseId;

    @TableField("source_sample_id")
    private String sourceSampleId;

    @TableField("text")
    private String text;

    @TableField("heading_path")
    private String headingPath;

    @TableField("page_start")
    private Integer pageStart;

    @TableField("page_end")
    private Integer pageEnd;

    @TableField("document_type")
    private String documentType;

    /** high / medium / low */
    @TableField("audit_priority")
    private String auditPriority;

    @TableField("audit_reason")
    private String auditReason;

    /** JSON 数组：命中信号列表 */
    @TableField("hit_signals")
    private String hitSignals;

    /** JSON 数组：用户标注的实体列表 */
    @TableField("gold_entities")
    private String goldEntities;

    /** JSON 数组：用户标注的关系列表 */
    @TableField("gold_relations")
    private String goldRelations;

    @TableField("annotation_notes")
    private String annotationNotes;

    /** pending / in_progress / completed / skipped */
    @TableField("reviewer_decision")
    private String reviewerDecision;

    @TableField("reviewer_confidence")
    private BigDecimal reviewerConfidence;

    @TableField("skip_reason")
    private String skipReason;

    @TableField("gold_stable_key")
    private String goldStableKey;

    @TableField("reused_from_build_run_id")
    private Long reusedFromBuildRunId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
