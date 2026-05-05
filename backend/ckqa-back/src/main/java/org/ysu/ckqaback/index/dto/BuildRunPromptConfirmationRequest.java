package org.ysu.ckqaback.index.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Prompt 确认请求。
 */
@Getter
@Setter
public class BuildRunPromptConfirmationRequest {

    private String promptStrategy = "active";

    private Boolean confirmed = false;
}
