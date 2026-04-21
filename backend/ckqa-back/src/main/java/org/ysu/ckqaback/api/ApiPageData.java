package org.ysu.ckqaback.api;

import lombok.Getter;

import java.util.List;

/**
 * 统一分页响应数据结构。
 *
 * @param <T> 列表项类型
 */
@Getter
public class ApiPageData<T> {

    /**
     * 当前页数据列表。
     */
    private final List<T> items;

    /**
     * 当前页码。
     */
    private final long current;

    /**
     * 每页大小。
     */
    private final long size;

    /**
     * 总记录数。
     */
    private final long total;

    /**
     * 总页数。
     */
    private final long pages;

    /**
     * 创建分页响应对象。
     *
     * @param items 当前页数据
     * @param current 当前页码
     * @param size 每页大小
     * @param total 总记录数
     * @param pages 总页数
     */
    public ApiPageData(List<T> items, long current, long size, long total, long pages) {
        this.items = items;
        this.current = current;
        this.size = size;
        this.total = total;
        this.pages = pages;
    }
}
