package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程列表查询参数。
 */
@Getter
@Setter
public class CourseQueryRequest {

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
     * 课程 ID、名称或描述关键字。
     */
    private String keyword;

    /**
     * 课程状态。
     */
    private String status;
}
