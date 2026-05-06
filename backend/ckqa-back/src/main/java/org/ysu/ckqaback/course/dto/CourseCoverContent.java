package org.ysu.ckqaback.course.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 课程封面二进制内容。
 */
@Getter
@Builder
public class CourseCoverContent {

    private final byte[] bytes;
    private final String contentType;
    private final Long fileSize;
}
