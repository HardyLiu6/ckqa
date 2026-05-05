package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 构建流水线更新请求。
 */
@Getter
@Setter
public class BuildRunUpdateRequest {

    @Pattern(regexp = "pending|running|success|failed|interrupted|archived", message = "status取值不合法")
    private String status;

    @Pattern(
            regexp = "material_selection|parse|graph_input_export|prompt|index|qa_smoke|done",
            message = "currentStage取值不合法"
    )
    private String currentStage;

    @Size(max = 20000, message = "buildMetadata长度不能超过20000")
    private String buildMetadata;
}
