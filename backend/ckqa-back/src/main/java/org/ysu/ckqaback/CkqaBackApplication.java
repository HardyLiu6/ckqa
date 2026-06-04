package org.ysu.ckqaback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.auth.config.CkqaEmailProperties;
import org.ysu.ckqaback.auth.config.CkqaSecurityProperties;
import org.ysu.ckqaback.cache.StudentRedisCacheProperties;
import org.ysu.ckqaback.config.QaDomainGuardProperties;
import org.ysu.ckqaback.course.CourseMaterialProperties;
import org.ysu.ckqaback.course.CourseCoverProperties;
import org.ysu.ckqaback.course.routing.CourseRoutingProperties;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.qa.stream.QaTaskStreamProperties;
import org.ysu.ckqaback.user.UserAvatarProperties;

import java.util.Map;

@MapperScan("org.ysu.ckqaback.mapper")
@EnableConfigurationProperties({
        CkqaIntegrationProperties.class,
        CourseCoverProperties.class,
        CourseMaterialProperties.class,
        UserAvatarProperties.class,
        CkqaSecurityProperties.class,
        CkqaEmailProperties.class,
        StudentRedisCacheProperties.class,
        QaTaskStreamProperties.class,
        CourseRoutingProperties.class,
        QaDomainGuardProperties.class
})
@SpringBootApplication
public class CkqaBackApplication {

    static final String DEVTOOLS_RESTART_ENABLED_PROPERTY = "spring.devtools.restart.enabled";
    static final String SPRING_DEVTOOLS_RESTART_ENABLED_ENV = "SPRING_DEVTOOLS_RESTART_ENABLED";
    static final String CKQA_DEVTOOLS_RESTART_ENABLED_ENV = "CKQA_DEVTOOLS_RESTART_ENABLED";

    public static void main(String[] args) {
        configureDevtoolsRestartDefault(System.getenv());
        SpringApplication.run(CkqaBackApplication.class, args);
    }

    static void configureDevtoolsRestartDefault(Map<String, String> env) {
        if (hasText(System.getProperty(DEVTOOLS_RESTART_ENABLED_PROPERTY))) {
            return;
        }
        String springDevtoolsEnv = env.get(SPRING_DEVTOOLS_RESTART_ENABLED_ENV);
        if (hasText(springDevtoolsEnv)) {
            System.setProperty(DEVTOOLS_RESTART_ENABLED_PROPERTY, springDevtoolsEnv);
            return;
        }
        String ckqaDevtoolsEnv = env.get(CKQA_DEVTOOLS_RESTART_ENABLED_ENV);
        if (hasText(ckqaDevtoolsEnv)) {
            System.setProperty(DEVTOOLS_RESTART_ENABLED_PROPERTY, ckqaDevtoolsEnv);
            return;
        }

        // 本地后端常与测试/构建命令并行运行；DevTools 热重启会在 target/classes 重建窗口误读主类。
        System.setProperty(DEVTOOLS_RESTART_ENABLED_PROPERTY, "false");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

}
