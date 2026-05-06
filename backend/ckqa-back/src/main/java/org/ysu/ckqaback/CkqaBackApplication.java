package org.ysu.ckqaback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.course.CourseCoverProperties;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;

@MapperScan("org.ysu.ckqaback.mapper")
@EnableConfigurationProperties({CkqaIntegrationProperties.class, CourseCoverProperties.class})
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
