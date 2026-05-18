package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * POST /extraction-eval 请求体。
 */
@Getter
@Setter
public class ExtractionEvalRequest {

    /** 用户在 03 步勾选的候选 ID 列表，至少 1 个。 */
    @NotEmpty(message = "selectedCandidates 不能为空")
    private List<String> selectedCandidates;
}
