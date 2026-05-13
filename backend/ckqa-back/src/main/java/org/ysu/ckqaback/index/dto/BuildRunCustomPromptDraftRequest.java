package org.ysu.ckqaback.index.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 手动调优提示词草稿保存请求体。
 */
@Getter
@Setter
public class BuildRunCustomPromptDraftRequest {

    @NotBlank(message = "seed 必填")
    private String seed;

    @NotNull(message = "prompts 必填")
    @Valid
    private Map<String, PromptBlock> prompts;

    @Getter
    @Setter
    public static class PromptBlock {
        @NotBlank(message = "content 必填")
        private String content;
    }
}
