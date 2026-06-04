package org.ysu.ckqaback.qa.safety;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 学生端事件/DTO 的敏感字段递归过滤器。
 */
public final class QaSensitiveFieldFilter {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "conversationhistory",
            "memoryhistoryjson",
            "memorysourcesjson",
            "memorytext",
            "contextsnapshottext",
            "semanticstatejson",
            "fullcontent",
            "generationcontext"
    );

    private QaSensitiveFieldFilter() {
    }

    public static Object sanitize(Object value) {
        if (value instanceof JsonNode node) {
            return sanitize(new ObjectMapper().convertValue(node, Object.class));
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (isSensitiveKey(key)) {
                    continue;
                }
                sanitized.put(key, sanitize(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>(list.size());
            for (Object item : list) {
                sanitized.add(sanitize(item));
            }
            return sanitized;
        }
        return value;
    }

    public static Map<String, Object> sanitizeMap(Map<String, Object> value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitized = (Map<String, Object>) sanitize(value == null ? Map.of() : value);
        return sanitized;
    }

    public static List<Map<String, Object>> sanitizeMapList(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> sanitized = new ArrayList<>(values.size());
        for (Map<String, Object> value : values) {
            sanitized.add(sanitizeMap(value));
        }
        return sanitized;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.replace("_", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.contains(normalized);
    }
}
