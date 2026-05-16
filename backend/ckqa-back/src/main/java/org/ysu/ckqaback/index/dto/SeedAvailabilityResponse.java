package org.ysu.ckqaback.index.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * GET /knowledge-base-build-runs/{id}/seed-availability 响应。
 * 列出 01 步每个 seed 选项的可用状态。
 */
@Getter
@Builder
public class SeedAvailabilityResponse {

    /** 当前 build run metadata 中已选定的种子（可能为空，前端用于回填）。 */
    private final String currentSeed;

    private final List<SeedOption> options;

    @Getter
    @Builder
    public static class SeedOption {
        /** system_default / graphrag_tuned / history_draft。 */
        private final String key;
        /** 是否可选。 */
        private final Boolean available;
        /** 不可选时的原因 key（前端做 i18n / tooltip 文案映射）。 */
        private final String reason;
        /** 给前端展示的简短描述（如自动调优产物的状态）。 */
        private final String summary;
    }
}
