package org.ysu.ckqaback.course;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 课程封面 MinIO 存储与访问配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.course-cover")
public class CourseCoverProperties {

    private String endpoint = "localhost:9000";

    private String accessKey = "admin";

    private String secretKey = "12345678";

    private boolean secure = false;

    private String bucketName = "course-artifacts";

    private String objectPrefix = "course-covers";

    private String publicPathPrefix = "/api/v1/course-covers";

    private long maxFileSizeBytes = 2L * 1024L * 1024L;

    public String normalizeObjectPrefix() {
        return objectPrefix == null
                ? ""
                : objectPrefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
