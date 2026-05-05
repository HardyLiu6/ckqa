package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 构建流水线创建请求。
 */
@Getter
@Setter
public class BuildRunCreateRequest {

    @Positive(message = "requestedByUserId必须大于0")
    private Long requestedByUserId;

    private List<@Positive(message = "materialIds必须大于0") Long> materialIds = List.of();

    private String jsonFile = "section_docs.json";

    private String promptStrategy = "active";

    private Boolean activateOnSuccess = true;
}
