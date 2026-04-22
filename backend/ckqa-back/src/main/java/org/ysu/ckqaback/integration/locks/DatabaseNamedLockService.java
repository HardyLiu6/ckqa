package org.ysu.ckqaback.integration.locks;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 基于 MySQL 命名锁的互斥服务。
 */
@Service
@RequiredArgsConstructor
public class DatabaseNamedLockService {

    private final JdbcTemplate jdbcTemplate;

    public boolean acquire(String lockName, int timeoutSeconds) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)",
                Integer.class,
                lockName,
                timeoutSeconds
        );
        return Integer.valueOf(1).equals(value);
    }

    public void release(String lockName) {
        jdbcTemplate.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, lockName);
    }
}
