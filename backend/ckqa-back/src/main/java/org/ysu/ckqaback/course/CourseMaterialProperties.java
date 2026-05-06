package org.ysu.ckqaback.course;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 课程资料对象存储配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.course-material")
public class CourseMaterialProperties {

    private String bucketName = "course-artifacts";

    private String objectPrefix = "course-materials";

    private long maxFileSizeBytes = 50L * 1024L * 1024L;

    public String normalizeObjectPrefix() {
        return objectPrefix == null
                ? ""
                : objectPrefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
