package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 学生端问答会话轻量更新请求。
 */
@Getter
@Setter
public class UpdateQaSessionRequest {

    @Size(max = 80, message = "title长度不能超过80")
    private String title;

    @Pattern(regexp = "active|archived", message = "status取值不合法")
    private String status;

    private Boolean isFavorite;
}
