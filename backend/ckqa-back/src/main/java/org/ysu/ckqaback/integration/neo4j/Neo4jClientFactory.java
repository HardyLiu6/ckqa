package org.ysu.ckqaback.integration.neo4j;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.integration.config.Neo4jProperties;

import jakarta.annotation.PreDestroy;

/**
 * 创建只读用的 Neo4j Driver。
 * <p>
 * Driver 在容器关闭时自动 close。所有连接信息通过 {@link Neo4jProperties} 注入，
 * 不在响应体中向前端暴露任何 URI / 凭据。
 * </p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(Neo4jProperties.class)
public class Neo4jClientFactory {

    private Driver driver;

    @Bean
    public Driver neo4jDriver(Neo4jProperties properties) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getUri())) {
            log.info("Neo4j 集成未启用：ckqa.neo4j.enabled=false 或 uri 未配置");
            return null;
        }
        try {
            this.driver = GraphDatabase.driver(
                    properties.getUri(),
                    AuthTokens.basic(properties.getUsername(), properties.getPassword())
            );
            log.info("Neo4j 客户端已初始化，uri={}", maskUri(properties.getUri()));
            return this.driver;
        } catch (Exception ex) {
            log.warn("Neo4j 客户端初始化失败，将以离线模式继续启动：{}", ex.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void close() {
        if (driver != null) {
            try {
                driver.close();
            } catch (Exception ex) {
                log.warn("Neo4j 驱动关闭异常：{}", ex.getMessage());
            }
        }
    }

    /**
     * 仅保留协议与端口，避免日志中出现完整主机或凭据。
     */
    static String maskUri(String uri) {
        if (uri == null) {
            return "<none>";
        }
        int schemeEnd = uri.indexOf("://");
        if (schemeEnd < 0) {
            return "***";
        }
        String scheme = uri.substring(0, schemeEnd);
        int portIdx = uri.lastIndexOf(':');
        if (portIdx > schemeEnd) {
            return scheme + "://***" + uri.substring(portIdx);
        }
        return scheme + "://***";
    }
}
