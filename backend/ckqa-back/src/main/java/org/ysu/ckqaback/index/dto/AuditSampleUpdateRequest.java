package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * PUT /audit-samples/{sampleId} 请求体。
 * <p>
 * 所有字段均可选——只下发本次需要更新的字段；后端按存在性合并。
 * </p>
 */
@Getter
@Setter
public class AuditSampleUpdateRequest {

    private List<Map<String, Object>> goldEntities;
    private List<Map<String, Object>> goldRelations;
    private String annotationNotes;

    /** pending / in_progress / completed / skipped。 */
    private String reviewerDecision;

    @DecimalMin(value = "0.00", message = "置信度不得低于 0")
    @DecimalMax(value = "1.00", message = "置信度不得高于 1")
    private BigDecimal reviewerConfidence;

    private String skipReason;
}
