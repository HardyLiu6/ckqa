package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * POST /finalize 请求体。
 * <p>
 * 用户从 04 排行榜选定一个候选并落库到 customPromptDraft，可选同时入库 prompt_drafts。
 * </p>
 */
@Getter
@Setter
public class FinalizePromptRequest {

    @NotBlank(message = "candidateId 必填")
    private String candidateId;

    /** true 时同时入库 prompt_drafts；默认 false。 */
    private Boolean saveAsDraft;

    @Size(max = 128, message = "草稿名最长 128 字符")
    private String draftName;

    private String draftDescription;
}
