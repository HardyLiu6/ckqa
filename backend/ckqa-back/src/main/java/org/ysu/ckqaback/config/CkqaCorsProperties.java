package org.ysu.ckqaback.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * CKQA Java API 跨域配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.cors")
public class CkqaCorsProperties {

    /**
     * 是否启用 CORS 过滤器。
     */
    private boolean enabled = true;

    /**
     * 允许访问 /api/v1/** 的来源模式。
     */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://127.0.0.1:*",
            "http://localhost:*"
    ));
}
