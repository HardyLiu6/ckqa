package org.ysu.ckqaback.auth.email;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.config.CkqaSecurityProperties;
import org.ysu.ckqaback.exception.BusinessException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;

/**
 * 邮箱验证码生成、校验、限频。
 *
 * <p>Redis 键名：
 * <ul>
 *   <li>{@code ckqa:auth:email-code:<email>} 当前未使用的验证码（TTL 5 分钟）</li>
 *   <li>{@code ckqa:auth:email-code-cooldown:<email>} 冷却 marker（TTL 60 秒，存在则禁止再发）</li>
 *   <li>{@code ckqa:auth:email-code-quota:<email>:<yyyy-mm-dd>} 当日发码计数（TTL 至次日 0 点）</li>
 * </ul></p>
 */
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);

    private static final String CODE_KEY_PREFIX = "ckqa:auth:email-code:";
    private static final String COOLDOWN_KEY_PREFIX = "ckqa:auth:email-code-cooldown:";
    private static final String QUOTA_KEY_PREFIX = "ckqa:auth:email-code-quota:";

    private final StringRedisTemplate redisTemplate;
    private final CkqaSecurityProperties securityProperties;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 发送验证码：检查冷却 + 当日额度，通过后写 Redis 并发邮件。
     */
    public void sendCode(String email) {
        String normalized = normalize(email);
        ensureCooldown(normalized);
        ensureDailyQuota(normalized);

        String code = generateCode();
        Duration ttl = securityProperties.getEmailCode().getTtl();
        redisTemplate.opsForValue().set(codeKey(normalized), code, ttl);
        // 写冷却（独立 key 防止 code 被 verify 消费后冷却随之消失）
        redisTemplate.opsForValue().set(cooldownKey(normalized), "1",
                securityProperties.getEmailCode().getSendCooldown());
        // 当日额度计数
        String quotaKey = quotaKey(normalized);
        Long current = redisTemplate.opsForValue().increment(quotaKey);
        if (current != null && current.equals(1L)) {
            redisTemplate.expire(quotaKey, Duration.ofDays(1));
        }

        String subject = "智课问答 · 登录验证码";
        String body = String.format(
                "您的登录验证码是 %s，%d 分钟内有效。\n\n如非本人操作请忽略本邮件。\n\n— 智课问答 (CKQA)",
                code, Math.max(1, ttl.toMinutes())
        );
        emailSender.send(normalized, subject, body);
        log.info("已发送邮箱验证码 email={}, ttlSeconds={}", normalized, ttl.getSeconds());
    }

    /**
     * 校验验证码；通过后立即作废（一次性）。
     */
    public void verifyAndConsume(String email, String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ApiResultCode.EMAIL_CODE_INVALID, HttpStatus.BAD_REQUEST);
        }
        String normalized = normalize(email);
        String stored = redisTemplate.opsForValue().get(codeKey(normalized));
        if (stored == null || !stored.equals(code.trim())) {
            throw new BusinessException(ApiResultCode.EMAIL_CODE_INVALID, HttpStatus.UNAUTHORIZED);
        }
        redisTemplate.delete(codeKey(normalized));
    }

    private void ensureCooldown(String email) {
        Boolean cooling = redisTemplate.hasKey(cooldownKey(email));
        if (Boolean.TRUE.equals(cooling)) {
            Long ttl = redisTemplate.getExpire(cooldownKey(email));
            String message = ttl != null && ttl > 0
                    ? String.format("发送过于频繁，请 %d 秒后重试", ttl)
                    : "发送过于频繁，请稍后重试";
            throw new BusinessException(ApiResultCode.EMAIL_CODE_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    private void ensureDailyQuota(String email) {
        String quotaKey = quotaKey(email);
        String value = redisTemplate.opsForValue().get(quotaKey);
        long current = 0L;
        if (value != null) {
            try {
                current = Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                // 当作 0
            }
        }
        int dailyQuota = securityProperties.getEmailCode().getDailyQuota();
        if (current >= dailyQuota) {
            throw new BusinessException(ApiResultCode.EMAIL_CODE_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                    "当日验证码发送次数已达上限，请明日再试");
        }
    }

    private String generateCode() {
        int length = Math.max(4, Math.min(8, securityProperties.getEmailCode().getLength()));
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(secureRandom.nextInt(10));
        }
        return sb.toString();
    }

    private String normalize(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        return email.trim().toLowerCase();
    }

    private String codeKey(String email) {
        return CODE_KEY_PREFIX + email;
    }

    private String cooldownKey(String email) {
        return COOLDOWN_KEY_PREFIX + email;
    }

    private String quotaKey(String email) {
        return QUOTA_KEY_PREFIX + email + ":" + LocalDate.now();
    }
}
