package org.ysu.ckqaback.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 本地签发配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.jwt")
public class JwtProperties {

    /**
     * 本地开发默认密钥。生产环境应通过环境变量覆盖。
     */
    private String secret = "ckqa-local-dev-jwt-secret-change-me-at-runtime-2026";

    private String issuer = "ckqa-back";

    private Duration ttl = Duration.ofHours(8);
}
