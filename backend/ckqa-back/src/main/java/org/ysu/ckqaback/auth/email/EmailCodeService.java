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
import java.util.Locale;
import java.util.Set;

/**
 * 邮箱验证码生成、校验、限频。
 *
 * <p>按 scene 隔离 Redis key，避免登录、注册、找回密码三类验证码相互覆盖：
 * <ul>
 *   <li>{@code ckqa:auth:email-code:<scene>:<email>} 当前未使用的验证码（TTL 5 分钟）</li>
 *   <li>{@code ckqa:auth:email-code-cooldown:<scene>:<email>} 冷却 marker（TTL 60 秒，存在则禁止再发）</li>
 *   <li>{@code ckqa:auth:email-code-quota:<email>:<yyyy-mm-dd>} 当日发码计数（TTL 至次日 0 点）所有 scene 共用</li>
 * </ul></p>
 */
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);

    private static final String CODE_KEY_PREFIX = "ckqa:auth:email-code:";
    private static final String COOLDOWN_KEY_PREFIX = "ckqa:auth:email-code-cooldown:";
    private static final String QUOTA_KEY_PREFIX = "ckqa:auth:email-code-quota:";

    public static final String SCENE_LOGIN = "login";
    public static final String SCENE_REGISTER = "register";
    public static final String SCENE_RESET_PASSWORD = "reset-password";

    private static final Set<String> ALLOWED_SCENES = Set.of(SCENE_LOGIN, SCENE_REGISTER, SCENE_RESET_PASSWORD);

    private final StringRedisTemplate redisTemplate;
    private final CkqaSecurityProperties securityProperties;
    private final EmailSender emailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 默认场景：登录用验证码（保持向后兼容）。
     */
    public void sendCode(String email) {
        sendCode(email, SCENE_LOGIN);
    }

    /**
     * 按场景下发验证码：检查冷却 + 当日额度，通过后写 Redis 并发邮件。
     */
    public void sendCode(String email, String scene) {
        String normalizedScene = normalizeScene(scene);
        String normalizedEmail = normalizeEmail(email);
        ensureCooldown(normalizedEmail, normalizedScene);
        ensureDailyQuota(normalizedEmail);

        String code = generateCode();
        Duration ttl = securityProperties.getEmailCode().getTtl();
        redisTemplate.opsForValue().set(codeKey(normalizedEmail, normalizedScene), code, ttl);
        redisTemplate.opsForValue().set(
                cooldownKey(normalizedEmail, normalizedScene),
                "1",
                securityProperties.getEmailCode().getSendCooldown()
        );
        String quotaKey = quotaKey(normalizedEmail);
        Long current = redisTemplate.opsForValue().increment(quotaKey);
        if (current != null && current.equals(1L)) {
            redisTemplate.expire(quotaKey, Duration.ofDays(1));
        }

        String subject = subjectFor(normalizedScene);
        String body = String.format(
                "您的%s验证码是 %s，%d 分钟内有效。\n\n如非本人操作请忽略本邮件。\n\n— 智课问答 (CKQA)",
                actionFor(normalizedScene), code, Math.max(1, ttl.toMinutes())
        );
        emailSender.send(normalizedEmail, subject, body);
        log.info("已发送邮箱验证码 email={}, scene={}, ttlSeconds={}",
                normalizedEmail, normalizedScene, ttl.getSeconds());
    }

    /**
     * 默认场景：登录验证码校验（保持向后兼容）。
     */
    public void verifyAndConsume(String email, String code) {
        verifyAndConsume(email, code, SCENE_LOGIN);
    }

    /**
     * 按场景校验验证码；通过后立即作废（一次性）。
     */
    public void verifyAndConsume(String email, String code, String scene) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ApiResultCode.EMAIL_CODE_INVALID, HttpStatus.BAD_REQUEST);
        }
        String normalizedScene = normalizeScene(scene);
        String normalizedEmail = normalizeEmail(email);
        String stored = redisTemplate.opsForValue().get(codeKey(normalizedEmail, normalizedScene));
        if (stored == null || !stored.equals(code.trim())) {
            throw new BusinessException(ApiResultCode.EMAIL_CODE_INVALID, HttpStatus.UNAUTHORIZED);
        }
        redisTemplate.delete(codeKey(normalizedEmail, normalizedScene));
    }

    private void ensureCooldown(String email, String scene) {
        Boolean cooling = redisTemplate.hasKey(cooldownKey(email, scene));
        if (Boolean.TRUE.equals(cooling)) {
            Long ttl = redisTemplate.getExpire(cooldownKey(email, scene));
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

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeScene(String scene) {
        String normalized = StringUtils.hasText(scene) ? scene.trim().toLowerCase(Locale.ROOT) : SCENE_LOGIN;
        if (!ALLOWED_SCENES.contains(normalized)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "不支持的验证码场景");
        }
        return normalized;
    }

    private String subjectFor(String scene) {
        return switch (scene) {
            case SCENE_REGISTER -> "智课问答 · 注册验证码";
            case SCENE_RESET_PASSWORD -> "智课问答 · 找回密码验证码";
            default -> "智课问答 · 登录验证码";
        };
    }

    private String actionFor(String scene) {
        return switch (scene) {
            case SCENE_REGISTER -> "注册";
            case SCENE_RESET_PASSWORD -> "找回密码";
            default -> "登录";
        };
    }

    private String codeKey(String email, String scene) {
        return CODE_KEY_PREFIX + scene + ":" + email;
    }

    private String cooldownKey(String email, String scene) {
        return COOLDOWN_KEY_PREFIX + scene + ":" + email;
    }

    private String quotaKey(String email) {
        return QUOTA_KEY_PREFIX + email + ":" + LocalDate.now();
    }
}
