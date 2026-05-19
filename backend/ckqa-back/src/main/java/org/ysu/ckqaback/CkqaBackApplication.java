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
import org.ysu.ckqaback.course.CourseMaterialProperties;
import org.ysu.ckqaback.course.CourseCoverProperties;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.user.UserAvatarProperties;

@MapperScan("org.ysu.ckqaback.mapper")
@EnableConfigurationProperties({
        CkqaIntegrationProperties.class,
        CourseCoverProperties.class,
        CourseMaterialProperties.class,
        UserAvatarProperties.class,
        CkqaSecurityProperties.class,
        CkqaEmailProperties.class,
        StudentRedisCacheProperties.class
})
@SpringBootApplication
public class CkqaBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(CkqaBackApplication.class, args);
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

}
