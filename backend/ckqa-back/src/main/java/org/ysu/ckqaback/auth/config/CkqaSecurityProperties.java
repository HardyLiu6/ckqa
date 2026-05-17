package org.ysu.ckqaback.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 登录限频与人机验证相关配置。
 *
 * <p>分三块：
 * <ul>
 *   <li>{@link Login}：失败次数阈值与锁定窗口，作用于密码登录与邮箱验证码登录两类入口</li>
 *   <li>{@link EmailCode}：邮箱验证码生成、TTL、发码限频、每日额度</li>
 *   <li>{@link Turnstile}：Cloudflare Turnstile 人机验证 token 校验</li>
 * </ul>
 * 默认值均偏保守；线上可通过 {@code application.properties} 或环境变量调整。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.security")
public class CkqaSecurityProperties {

    private final Login login = new Login();
    private final EmailCode emailCode = new EmailCode();
    private final Turnstile turnstile = new Turnstile();

    @Getter
    @Setter
    public static class Login {
        /** 失败计数阈值；达到后触发锁定。默认 5 次。 */
        private int maxFailures = 5;
        /** 失败计数窗口；超出窗口未达阈值则失败计数自然过期。默认 15 分钟。 */
        private Duration failureWindow = Duration.ofMinutes(15);
        /** 触发锁定后的冻结时长；锁定期间任意登录尝试均拒绝。默认 5 分钟。 */
        private Duration lockDuration = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class EmailCode {
        /** 验证码长度（位数），默认 6 位纯数字。 */
        private int length = 6;
        /** 验证码 TTL，超过后即失效。默认 5 分钟。 */
        private Duration ttl = Duration.ofMinutes(5);
        /** 发码冷却：同一邮箱两次发送的最小间隔。默认 60 秒。 */
        private Duration sendCooldown = Duration.ofSeconds(60);
        /** 同一邮箱每日最多发送次数（按自然日 / Asia/Shanghai 0 点重置）。默认 5 次。 */
        private int dailyQuota = 5;
    }

    @Getter
    @Setter
    public static class Turnstile {
        /** 是否启用 Cloudflare Turnstile token 校验。开发态默认 false。 */
        private boolean enabled = false;
        /** Turnstile 服务端密钥，配合前端 site key 使用；enabled=true 时必填。 */
        private String secretKey = "";
        /** Cloudflare 验证 endpoint。 */
        private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    }
}
