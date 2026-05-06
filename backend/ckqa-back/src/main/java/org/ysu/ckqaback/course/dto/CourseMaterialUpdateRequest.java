package org.ysu.ckqaback.course.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程资料元数据更新请求。
 */
@Getter
@Setter
public class CourseMaterialUpdateRequest {

    @Size(max = 255, message = "displayName长度不能超过255")
    private String displayName;

    @Pattern(regexp = "textbook|handout|slides|lab_guide|exam|reference|other", message = "materialType取值不合法")
    private String materialType;
}
