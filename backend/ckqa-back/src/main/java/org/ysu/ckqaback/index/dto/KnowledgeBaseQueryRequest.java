package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识库列表查询参数。
 */
@Getter
@Setter
public class KnowledgeBaseQueryRequest {

    /**
     * 当前页码，从 1 开始。
     */
    @Min(value = 1, message = "page 不能小于 1")
    private Integer page = 1;

    /**
     * 每页大小，最多 100 条。
     */
    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 不能大于 100")
    private Integer size = 20;

    /**
     * 课程 ID、知识库编码、名称或描述关键字。
     */
    private String keyword;

    /**
     * 知识库状态。
     */
    private String status;
}
