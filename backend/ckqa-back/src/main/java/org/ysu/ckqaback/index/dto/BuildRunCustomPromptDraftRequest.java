package org.ysu.ckqaback.index.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 手动调优提示词草稿保存请求体。
 *
 * <p>支持两种模式：</p>
 * <ul>
 *   <li>全量更新：同时传 seed 和 prompts，新内容覆盖旧值（Phase 1 行为）。</li>
 *   <li>部分更新：仅传 seed，prompts 留空。后端合并保留 build run 中已有的 prompts，
 *       仅刷新 seed 与 seedSnapshotAt（Phase 4.5 起支持）。</li>
 * </ul>
 *
 * <p>这样前端在用户切换 seed 时不必重新拼装 prompts 包，避免 4001 校验失败。</p>
 */
@Getter
@Setter
public class BuildRunCustomPromptDraftRequest {

    @NotBlank(message = "seed 必填")
    private String seed;

    /**
     * 提示词内容。可选：null 时走部分更新路径，仅刷新 seed；
     * 非 null 时仍由 {@link PromptBlock} 上的 {@code @NotBlank} 校验内容必填。
     */
    @Valid
    private Map<String, PromptBlock> prompts;

    @Getter
    @Setter
    public static class PromptBlock {
        @NotBlank(message = "content 必填")
        private String content;
    }
}
