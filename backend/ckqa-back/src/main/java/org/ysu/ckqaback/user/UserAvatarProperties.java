package org.ysu.ckqaback.user;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用户头像 MinIO 存储与访问配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.user-avatar")
public class UserAvatarProperties {

    private String bucketName = "course-artifacts";

    private String objectPrefix = "user-avatars";

    private String publicPathPrefix = "/api/v1/user-avatars";

    private long maxFileSizeBytes = 2L * 1024L * 1024L;

    public String normalizeObjectPrefix() {
        return objectPrefix == null
                ? ""
                : objectPrefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }
}
