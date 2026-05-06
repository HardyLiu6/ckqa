package org.ysu.ckqaback.course.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 课程封面上传结果。
 */
@Getter
@Builder
public class CourseCoverUploadResponse {

    private final String coverUrl;
    private final String fileName;
    private final String contentType;
    private final Long fileSize;
}
