package org.ysu.ckqaback.qa.context;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前可用主题栈。activeTopicsJson 是内部诊断锚点，不直接暴露给学生端。
 */
public record QaTopicStack(
        String latestTopic,
        String latestTopicMessageRange,
        String topicSource,
        Double topicConfidence,
        List<String> activeTopics,
        String activeTopicsJson
) {

    public static QaTopicStack empty() {
        return new QaTopicStack("", "", "", null, List.of(), "");
    }

    public static QaTopicStack of(String latestTopic, String range, String source, Double confidence, List<String> topics) {
        List<String> normalizedTopics = normalizeTopics(topics);
        if (StringUtils.hasText(latestTopic) && !normalizedTopics.contains(latestTopic)) {
            normalizedTopics = new ArrayList<>(normalizedTopics);
            normalizedTopics.add(latestTopic);
        }
        return new QaTopicStack(
                trimToEmpty(latestTopic),
                trimToEmpty(range),
                trimToEmpty(source),
                confidence,
                List.copyOf(normalizedTopics),
                toJson(normalizedTopics)
        );
    }

    public boolean hasTopic() {
        return StringUtils.hasText(latestTopic);
    }

    private static List<String> normalizeTopics(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String topic : topics) {
            String value = trimToEmpty(topic);
            if (StringUtils.hasText(value) && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private static String toJson(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < topics.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append("{\"topic\":\"").append(escapeJson(topics.get(index))).append("\"}");
        }
        return builder.append(']').toString();
    }

    private static String escapeJson(String value) {
        return trimToEmpty(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
