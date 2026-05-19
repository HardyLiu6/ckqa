package org.ysu.ckqaback.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonCreator
    public ApiPageData(
            @JsonProperty("items") List<T> items,
            @JsonProperty("current") long current,
            @JsonProperty("size") long size,
            @JsonProperty("total") long total,
            @JsonProperty("pages") long pages
    ) {
        this.items = items;
        this.current = current;
        this.size = size;
        this.total = total;
        this.pages = pages;
    }
}
