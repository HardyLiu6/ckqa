package org.ysu.ckqaback.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册全局 {@link ObjectMapper} bean。
 * <p>
 * Spring Boot 4.x 的 {@code spring-boot-starter-webmvc} 不再自动注册
 * {@code ObjectMapper} bean（与 3.x 的 {@code spring-boot-starter-web} 行为不同）。
 * 本配置确保所有通过构造器注入 {@code ObjectMapper} 的 service 能正常启动。
 * </p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
