package org.ysu.ckqaback.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.ysu.ckqaback.config.JacksonConfig;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class StudentRedisCacheServiceTest {

    @Test
    void shouldReturnCachedJsonWithoutCallingLoader() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mockValueOperations(redisTemplate);
        given(valueOperations.get("cache-key")).willReturn("{\"value\":\"cached\"}");
        StudentRedisCacheService service = new StudentRedisCacheService(
                redisTemplate,
                new ObjectMapper(),
                new StudentRedisCacheProperties()
        );

        CachePayload payload = service.getOrLoad(
                "cache-key",
                CachePayload.class,
                Duration.ofMinutes(1),
                () -> new CachePayload("loader")
        );

        assertThat(payload.value()).isEqualTo("cached");
    }

    @Test
    void shouldWriteLoaderResultWithTtlOnMiss() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mockValueOperations(redisTemplate);
        given(valueOperations.get("cache-key")).willReturn(null);
        StudentRedisCacheService service = new StudentRedisCacheService(
                redisTemplate,
                new ObjectMapper(),
                new StudentRedisCacheProperties()
        );

        CachePayload payload = service.getOrLoad(
                "cache-key",
                CachePayload.class,
                Duration.ofSeconds(30),
                () -> new CachePayload("fresh")
        );

        assertThat(payload.value()).isEqualTo("fresh");
        then(valueOperations).should().set(eq("cache-key"), eq("{\"value\":\"fresh\"}"), eq(Duration.ofSeconds(30)));
    }

    @Test
    void shouldSerializeJavaTimeValuesWithConfiguredObjectMapper() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mockValueOperations(redisTemplate);
        StudentRedisCacheService service = new StudentRedisCacheService(
                redisTemplate,
                new JacksonConfig().objectMapper(),
                new StudentRedisCacheProperties()
        );

        service.put(
                "cache-key",
                new TimePayload(LocalDateTime.of(2026, 5, 20, 18, 53, 33)),
                Duration.ofMinutes(1)
        );

        then(valueOperations).should().set(
                eq("cache-key"),
                eq("{\"updatedAt\":\"2026-05-20T18:53:33\"}"),
                eq(Duration.ofMinutes(1))
        );
    }

    @Test
    void shouldFailOpenWhenRedisReadFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mockValueOperations(redisTemplate);
        given(valueOperations.get("cache-key")).willThrow(new IllegalStateException("redis down"));
        StudentRedisCacheService service = new StudentRedisCacheService(
                redisTemplate,
                new ObjectMapper(),
                new StudentRedisCacheProperties()
        );
        AtomicInteger loaderCalls = new AtomicInteger();

        CachePayload payload = service.getOrLoad(
                "cache-key",
                CachePayload.class,
                Duration.ofSeconds(30),
                () -> {
                    loaderCalls.incrementAndGet();
                    return new CachePayload("fallback");
                }
        );

        assertThat(payload.value()).isEqualTo("fallback");
        assertThat(loaderCalls).hasValue(1);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> mockValueOperations(StringRedisTemplate redisTemplate) {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        return valueOperations;
    }

    private record CachePayload(String value) {
    }

    private record TimePayload(LocalDateTime updatedAt) {
    }
}
