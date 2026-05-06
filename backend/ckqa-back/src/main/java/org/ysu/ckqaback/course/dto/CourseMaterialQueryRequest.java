package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程资料列表查询参数。
 */
@Getter
@Setter
public class CourseMaterialQueryRequest {

    @Min(value = 1, message = "page 不能小于 1")
    private Integer page = 1;

    @Min(value = 1, message = "size 不能小于 1")
    @Max(value = 100, message = "size 不能大于 100")
    private Integer size = 20;

    @Size(max = 128, message = "keyword长度不能超过128")
    private String keyword;

    @Pattern(regexp = "textbook|handout|slides|lab_guide|exam|reference|other", message = "materialType取值不合法")
    private String materialType;

    @Pattern(regexp = "pending|processing|done|failed", message = "parseStatus取值不合法")
    private String parseStatus;
}
