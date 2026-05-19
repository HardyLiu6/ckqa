package org.ysu.ckqaback.cache;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 学生端服务端缓存配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.student-cache")
public class StudentRedisCacheProperties {

    private boolean enabled = true;
    private String prefix = "ckqa:student-cache:v1";
    private Duration courseTtl = Duration.ofSeconds(60);
    private Duration courseKbTtl = Duration.ofSeconds(60);
    private Duration routingTtl = Duration.ofMinutes(5);
    private Duration hybridReadyTtl = Duration.ofMinutes(5);
    private Duration hybridNotReadyTtl = Duration.ofSeconds(15);
}
