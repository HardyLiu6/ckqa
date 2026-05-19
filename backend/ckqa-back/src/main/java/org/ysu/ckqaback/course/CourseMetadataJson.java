package org.ysu.ckqaback.course;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 课程元数据字段（tags / objectives / audience）在 JSON 字符串与 {@code List<String>} 之间的转换工具。
 * <p>
 * 仓库现行约定：JSON 字段在实体里以 {@code String} 保存（参考 users.extra_metadata 等），
 * 由 service 层手工序列化与反序列化。本工具集中处理空值、非数组、解析失败等边界情况，
 * 避免在多个 service 里重复编写。
 * </p>
 */
public final class CourseMetadataJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private CourseMetadataJson() {
    }

    /**
     * 把 {@code List<String>} 序列化为 JSON 字符串。
     * 传入 {@code null} 或空列表时返回 {@code null}（库里继续保持为空）。
     */
    public static String toJsonOrNull(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception ex) {
            // 序列化失败属于编程错误，转 IllegalArgumentException 由全局异常处理
            throw new IllegalArgumentException("课程元数据序列化失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 把 JSON 字符串反序列化为不可变 {@code List<String>}。
     * 遇到 {@code null} / 空字符串 / 非数组返回 {@link List#of()}。
     */
    public static List<String> fromJsonOrEmpty(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = MAPPER.readValue(raw, STRING_LIST);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception ex) {
            // 反序列化失败保持降级为空列表，避免学生端因脏数据加载失败
            return List.of();
        }
    }
}
