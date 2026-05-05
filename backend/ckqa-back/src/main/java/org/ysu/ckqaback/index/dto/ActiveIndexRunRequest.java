package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 激活索引运行请求。
 */
@Getter
@Setter
public class ActiveIndexRunRequest {

    @NotNull(message = "indexRunId不能为空")
    @Positive(message = "indexRunId必须大于0")
    private Long indexRunId;
}
