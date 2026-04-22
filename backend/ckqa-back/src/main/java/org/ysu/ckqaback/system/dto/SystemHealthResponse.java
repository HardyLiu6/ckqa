package org.ysu.ckqaback.system.dto;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 系统健康检查响应体。
 */
@Getter
public class SystemHealthResponse {

    private final boolean up;
    private final List<SystemHealthItemResponse> items;

    public SystemHealthResponse(boolean up, List<SystemHealthItemResponse> items) {
        this.up = up;
        this.items = items;
    }

    public static SystemHealthResponse up(SystemHealthItemResponse... items) {
        return new SystemHealthResponse(true, Arrays.asList(items));
    }
}
