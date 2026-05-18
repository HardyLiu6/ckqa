package org.ysu.ckqaback.auth.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.config.CkqaSecurityProperties;
import org.ysu.ckqaback.exception.BusinessException;

import java.util.Map;

/**
 * Cloudflare Turnstile 人机验证 token 校验。
 *
 * <p>开关在 {@code ckqa.security.turnstile.enabled}：
 * <ul>
 *   <li>{@code false}（默认）：直接放行，不调用 Cloudflare，便于本地开发与单测</li>
 *   <li>{@code true}：调用 {@code https://challenges.cloudflare.com/turnstile/v0/siteverify}
 *       验证前端提交的 token。任何失败抛 4294 HUMAN_VERIFICATION_FAILED</li>
 * </ul></p>
 */
@Component
@RequiredArgsConstructor
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    private final CkqaSecurityProperties securityProperties;
    private final RestClient.Builder restClientBuilder;

    /**
     * 校验 token；未启用时立即返回。失败抛 4294。
     *
     * @param token    前端 turnstile widget 回传的 cf-turnstile-response
     * @param remoteIp 客户端 IP，可选；Cloudflare 用作风险评估
     */
    public void verify(String token, String remoteIp) {
        CkqaSecurityProperties.Turnstile config = securityProperties.getTurnstile();
        if (!config.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ApiResultCode.HUMAN_VERIFICATION_FAILED, HttpStatus.BAD_REQUEST,
                    "缺少人机验证 token");
        }
        if (!StringUtils.hasText(config.getSecretKey())) {
            log.warn("Turnstile 已启用但未配置 secret-key，登录请求被拒绝");
            throw new BusinessException(ApiResultCode.HUMAN_VERIFICATION_FAILED, HttpStatus.SERVICE_UNAVAILABLE,
                    "人机验证服务未配置");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", config.getSecretKey());
        form.add("response", token);
        if (StringUtils.hasText(remoteIp)) {
            form.add("remoteip", remoteIp);
        }

        try {
            Map<?, ?> response = restClientBuilder.build()
                    .post()
                    .uri(config.getVerifyUrl())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object success = response == null ? null : response.get("success");
            if (Boolean.TRUE.equals(success)) {
                return;
            }
            log.warn("Turnstile 校验失败 response={}", response);
        } catch (RuntimeException exception) {
            log.warn("Turnstile 调用异常：{}", exception.getMessage());
        }
        throw new BusinessException(ApiResultCode.HUMAN_VERIFICATION_FAILED, HttpStatus.FORBIDDEN);
    }
}
