package org.ysu.ckqaback.index.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * QA 冒烟验证请求。
 */
@Getter
@Setter
public class BuildRunQaSmokeRequest {

    @Size(max = 1000, message = "question长度不能超过1000")
    private String question;

    @Pattern(regexp = "local|global|drift|basic", message = "mode取值不合法")
    private String mode = "basic";
}
