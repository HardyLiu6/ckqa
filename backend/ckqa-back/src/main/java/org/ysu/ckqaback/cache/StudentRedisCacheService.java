package org.ysu.ckqaback.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 学生端 Redis 缓存门面。
 * <p>Redis 只做可丢弃读缓存；任何异常都回源，不能影响正式问答链路。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentRedisCacheService {

    private static final int SCAN_BATCH_SIZE = 500;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final StudentRedisCacheProperties properties;

    public <T> T getOrLoad(String key, Class<T> valueType, Duration ttl, Supplier<T> loader) {
        T cached = get(key, valueType);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        put(key, loaded, ttl);
        return loaded;
    }

    public <T> T getOrLoad(String key, TypeReference<T> valueType, Duration ttl, Supplier<T> loader) {
        T cached = get(key, valueType);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        put(key, loaded, ttl);
        return loaded;
    }

    public <T> T get(String key, Class<T> valueType) {
        if (!cacheEnabled(key)) {
            return null;
        }
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return objectMapper.readValue(raw, valueType);
        } catch (Exception exception) {
            log.warn("学生端 Redis 缓存读取失败，key={}，error={}", key, exception.toString());
            return null;
        }
    }

    public <T> T get(String key, TypeReference<T> valueType) {
        if (!cacheEnabled(key)) {
            return null;
        }
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(raw)) {
                return null;
            }
            return objectMapper.readValue(raw, valueType);
        } catch (Exception exception) {
            log.warn("学生端 Redis 缓存读取失败，key={}，error={}", key, exception.toString());
            return null;
        }
    }

    public void put(String key, Object value, Duration ttl) {
        if (!cacheEnabled(key) || value == null || ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException exception) {
            log.warn("学生端 Redis 缓存序列化失败，key={}，error={}", key, exception.toString());
        } catch (Exception exception) {
            log.warn("学生端 Redis 缓存写入失败，key={}，error={}", key, exception.toString());
        }
    }

    public void evictByPattern(String pattern) {
        if (!properties.isEnabled() || !StringUtils.hasText(pattern)) {
            return;
        }
        try {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();
                List<byte[]> batch = new ArrayList<>(SCAN_BATCH_SIZE);
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());
                        if (batch.size() >= SCAN_BATCH_SIZE) {
                            connection.del(batch.toArray(byte[][]::new));
                            batch.clear();
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    connection.del(batch.toArray(byte[][]::new));
                }
                return null;
            });
        } catch (Exception exception) {
            log.warn("学生端 Redis 缓存清理失败，pattern={}，error={}", pattern, exception.toString());
        }
    }

    public boolean ping() {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception exception) {
            log.warn("Redis PING 失败，error={}", exception.toString());
            return false;
        }
    }

    public Duration courseTtl() {
        return properties.getCourseTtl();
    }

    public Duration courseKnowledgeBaseTtl() {
        return properties.getCourseKbTtl();
    }

    public Duration routingTtl() {
        return properties.getRoutingTtl();
    }

    public Duration hybridReadyTtl() {
        return properties.getHybridReadyTtl();
    }

    public Duration hybridNotReadyTtl() {
        return properties.getHybridNotReadyTtl();
    }

    private boolean cacheEnabled(String key) {
        return properties.isEnabled() && StringUtils.hasText(key);
    }
}
