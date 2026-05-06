package org.ysu.ckqaback.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.ysu.ckqaback.api.ApiPaths;

import java.util.List;

/**
 * 本地开发前端跨域配置。
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CkqaCorsProperties.class)
public class WebCorsConfig {

    private final CkqaCorsProperties properties;

    /**
     * 仅允许本机开发来源访问 Java /api/v1 边界。
     *
     * @return CORS 过滤器注册对象
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(properties.getAllowedOriginPatterns());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-CKQA-User-Code"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(ApiPaths.API_V1 + "/**", configuration);
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setEnabled(properties.isEnabled());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
