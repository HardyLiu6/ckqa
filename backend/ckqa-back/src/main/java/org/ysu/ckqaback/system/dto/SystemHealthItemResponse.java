package org.ysu.ckqaback.system.dto;

import lombok.Getter;

/**
 * 健康检查子项响应体。
 */
@Getter
public class SystemHealthItemResponse {

    private final String name;
    private final boolean reachable;
    private final boolean ready;
    private final String message;

    private SystemHealthItemResponse(String name, boolean reachable, boolean ready, String message) {
        this.name = name;
        this.reachable = reachable;
        this.ready = ready;
        this.message = message;
    }

    public static SystemHealthItemResponse of(String name, boolean reachable, boolean ready, String message) {
        return new SystemHealthItemResponse(name, reachable, ready, message);
    }
}
