package org.ysu.ckqaback.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthConstants;
import org.ysu.ckqaback.auth.JwtProperties;
import org.ysu.ckqaback.auth.JwtTokenService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Security JWT 鉴权配置。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.objectMapper = objectMapperProvider.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules());
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JwtTokenService jwtTokenService) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                ApiPaths.AUTH + "/admin/login",
                                ApiPaths.AUTH + "/student/login",
                                ApiPaths.AUTH + "/student/register",
                                ApiPaths.AUTH + "/email/send-code",
                                ApiPaths.AUTH + "/email/admin/login",
                                ApiPaths.SYSTEM_HEALTH,
                                ApiPaths.API_V1 + "/course-covers/**",
                                ApiPaths.API_V1 + "/user-avatars/**",
                                ApiPaths.PDF_FILES + "/*/parse-events"
                        ).permitAll()
                        .requestMatchers(ApiPaths.API_V1 + "/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .jwt(jwt -> jwt
                                .decoder(jwtTokenService.getDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName(AuthConstants.USER_CODE_CLAIM);
        converter.setJwtGrantedAuthoritiesConverter(this::authoritiesFromJwt);
        return converter;
    }

    private List<GrantedAuthority> authoritiesFromJwt(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        List<String> permissions = jwt.getClaimAsStringList(AuthConstants.PERMISSIONS_CLAIM);
        if (permissions != null) {
            permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        }
        List<String> roles = jwt.getClaimAsStringList(AuthConstants.ROLES_CLAIM);
        if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
        }
        return authorities;
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(
                    response.getWriter(),
                    securityError(ApiResultCode.AUTH_REQUIRED)
            );
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(
                    response.getWriter(),
                    securityError(ApiResultCode.AUTH_FORBIDDEN)
            );
        };
    }

    private Map<String, Object> securityError(ApiResultCode resultCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", resultCode.getCode());
        body.put("message", resultCode.getMessage());
        body.put("data", null);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }
}
