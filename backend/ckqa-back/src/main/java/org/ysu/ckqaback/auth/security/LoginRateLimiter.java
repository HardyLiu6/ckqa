package org.ysu.ckqaback.auth.security;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.config.CkqaSecurityProperties;
import org.ysu.ckqaback.exception.BusinessException;

import java.time.Duration;

/**
 * 登录失败计数与锁定。
 *
 * <p>采用滑动窗口失败计数：每次失败 {@code INCR} 一个 key（{@code login-fail:<bucket>}），
 * 第一次失败时设置 {@code failureWindow} 过期；累计达到 {@code maxFailures} 时把对应
 * {@code login-lock:<bucket>} 设为 1 并加上 {@code lockDuration} 过期。锁定期间
 * {@link #ensureNotLocked} 会直接抛出 {@link BusinessException}（HTTP 429）。</p>
 *
 * <p>bucket 由调用方拼装；典型组合是 {@code username + ":" + clientIp}。</p>
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    private static final String FAIL_KEY_PREFIX = "ckqa:auth:login-fail:";
    private static final String LOCK_KEY_PREFIX = "ckqa:auth:login-lock:";

    private final StringRedisTemplate redisTemplate;
    private final CkqaSecurityProperties securityProperties;

    /**
     * 检查 bucket 是否被锁定；锁定中直接抛 4290 LOGIN_RATE_LIMITED。
     */
    public void ensureNotLocked(String bucket) {
        String key = lockKey(bucket);
        Boolean locked = redisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(locked)) {
            Long ttl = redisTemplate.getExpire(key);
            String message = ttl != null && ttl > 0
                    ? String.format("登录失败次数过多，请 %d 秒后重试", ttl)
                    : "登录失败次数过多，请稍后重试";
            throw new BusinessException(ApiResultCode.LOGIN_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    /**
     * 记一次失败：失败计数 + 1，达到阈值后写入 lock key。
     */
    public void recordFailure(String bucket) {
        if (!StringUtils.hasText(bucket)) {
            return;
        }
        String failKey = failKey(bucket);
        Long current = redisTemplate.opsForValue().increment(failKey);
        if (current != null && current.equals(1L)) {
            redisTemplate.expire(failKey, getFailureWindow());
        }
        int max = securityProperties.getLogin().getMaxFailures();
        if (current != null && current >= max) {
            String lockKey = lockKey(bucket);
            redisTemplate.opsForValue().set(lockKey, "1", getLockDuration());
            // 清空失败计数，避免锁定期满后第一次失败立即再次锁定
            redisTemplate.delete(failKey);
            log.warn("登录失败累计 {} 次，bucket={} 已锁定 {} 秒", current, bucket, getLockDuration().getSeconds());
        }
    }

    /**
     * 登录成功后清掉失败计数与锁。
     */
    public void recordSuccess(String bucket) {
        if (!StringUtils.hasText(bucket)) {
            return;
        }
        redisTemplate.delete(failKey(bucket));
        redisTemplate.delete(lockKey(bucket));
    }

    /**
     * 当前已累计失败次数（仅用于日志 / 测试）。
     */
    public long currentFailures(String bucket) {
        String value = redisTemplate.opsForValue().get(failKey(bucket));
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private String failKey(String bucket) {
        return FAIL_KEY_PREFIX + bucket;
    }

    private String lockKey(String bucket) {
        return LOCK_KEY_PREFIX + bucket;
    }

    private Duration getFailureWindow() {
        Duration d = securityProperties.getLogin().getFailureWindow();
        return d != null && !d.isZero() ? d : Duration.ofMinutes(15);
    }

    private Duration getLockDuration() {
        Duration d = securityProperties.getLogin().getLockDuration();
        return d != null && !d.isZero() ? d : Duration.ofMinutes(5);
    }
}
