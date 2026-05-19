package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理端来源人工标注请求。
 */
@Getter
@Setter
public class UpsertQaSourceReviewRequest {

    @NotBlank(message = "relevance不能为空")
    private String relevance;

    @NotBlank(message = "citationQuality不能为空")
    private String citationQuality;

    @Size(max = 500, message = "标注意见不能超过500字")
    private String note;
}
