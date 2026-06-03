package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 学生端智能推荐诊断快照。
 * <p>
 * 该对象主要用于运维观测；当请求 mode=smart 时，后端会读取 recommendedMode 做无额外 LLM 成本的执行模式解析。
 * </p>
 */
@Getter
@Setter
public class QaClientRoutingSnapshot {

    @Pattern(regexp = "local|global|drift|basic|hybrid_v0|smart", message = "selectedMode取值不合法")
    private String selectedMode;

    @Pattern(regexp = "local|global|drift|basic|hybrid_v0", message = "recommendedMode取值不合法")
    private String recommendedMode;

    @Pattern(regexp = "local|global|drift|basic|hybrid_v0", message = "fallbackMode取值不合法")
    private String fallbackMode;

    @DecimalMin(value = "0.0", message = "confidence不能小于0")
    @DecimalMax(value = "1.0", message = "confidence不能大于1")
    private Double confidence;

    @Pattern(regexp = "high_confidence|medium_confidence|low_confidence|uncertain", message = "confidenceBand取值不合法")
    private String confidenceBand;

    @Pattern(regexp = "normal|low_confidence|hybrid_not_ready", message = "reviewPriority取值不合法")
    private String reviewPriority;

    private Boolean manualSwitchSuggested;

    @Size(max = 8, message = "reasons最多8项")
    private List<@Size(max = 64, message = "reason长度不能超过64") String> reasons;

    @Size(max = 5, message = "routeScores最多5项")
    private Map<
            @Pattern(regexp = "local|global|drift|basic|hybrid_v0", message = "routeScores key不合法") String,
            @DecimalMin(value = "0.0", message = "route score不能小于0") @DecimalMax(value = "2.0", message = "route score不能大于2") Double
            > routeScores;
}
