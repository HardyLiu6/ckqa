package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 构建资料选择请求。
 */
@Getter
@Setter
public class BuildRunMaterialSelectionRequest {

    private List<@Positive(message = "materialIds必须大于0") Long> materialIds;
}
