package org.ysu.ckqaback.qa.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话语义状态 v1：仅用于 Java 侧持久化诊断，不进入学生端公开问答协议。
 */
public record SessionSemanticState(String version, String json) {

    public static final String VERSION = "session_semantic_state_v1";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static SessionSemanticState empty() {
        return from(QaTopicStack.empty(), null, false);
    }

    public static String emptyJson() {
        return empty().json();
    }

    public static SessionSemanticState from(QaTopicStack topicStack, QaContextSummary summary) {
        return from(topicStack, summary, hasSummaryState(summary));
    }

    public static SessionSemanticState fromTopicFields(
            String latestTopic,
            String latestTopicMessageRange,
            String topicSource,
            Double topicConfidence,
            String activeTopicsJson
    ) {
        QaTopicStack topicStack = QaTopicStack.of(
                latestTopic,
                latestTopicMessageRange,
                topicSource,
                topicConfidence,
                topicsFromJson(activeTopicsJson),
                comparisonTopicsFromJson(activeTopicsJson)
        );
        return from(topicStack, null, false);
    }

    public static SessionSemanticState from(
            QaTopicStack topicStack,
            QaContextSummary summary,
            boolean restoredFromSummary
    ) {
        QaTopicStack safeStack = topicStack == null ? QaTopicStack.empty() : topicStack;
        List<TopicItem> activeTopics = resolveActiveTopics(safeStack, summary);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", VERSION);
        payload.put("latestTopic", trimToEmpty(safeStack.latestTopic()));
        payload.put("latestTopicMessageRange", trimToEmpty(safeStack.latestTopicMessageRange()));
        payload.put("topicSource", trimToEmpty(safeStack.topicSource()));
        payload.put("topicConfidence", safeStack.topicConfidence());
        payload.put("activeTopics", toJsonItems(activeTopics));
        payload.put("comparisonTopics", toJsonItems(comparisonTopics(activeTopics)));
        payload.put("restoredFromSummary", restoredFromSummary);
        payload.put("summaryUntilSequenceNo", summary == null ? null : summary.untilSequenceNo());
        return new SessionSemanticState(VERSION, writeJson(payload));
    }

    private static boolean hasSummaryState(QaContextSummary summary) {
        return summary != null
                && (summary.untilSequenceNo() > 0
                || StringUtils.hasText(summary.latestTopic())
                || StringUtils.hasText(summary.activeTopicsJson())
                || StringUtils.hasText(summary.semanticStateJson()));
    }

    private static List<TopicItem> resolveActiveTopics(QaTopicStack topicStack, QaContextSummary summary) {
        List<TopicItem> topics = parseTopicItems(topicStack.activeTopicsJson());
        if (topics.isEmpty() && summary != null) {
            topics = parseTopicItems(summary.activeTopicsJson());
        }
        if (topics.isEmpty() && summary != null) {
            topics = parseSemanticTopicItems(summary.semanticStateJson());
        }
        if (topics.isEmpty()) {
            topics = new ArrayList<>();
            for (String topic : topicStack.activeTopics()) {
                addTopic(topics, topic, "");
            }
        }
        addTopic(topics, topicStack.latestTopic(), roleOf(topics, topicStack.latestTopic()));
        return topics;
    }

    private static List<TopicItem> parseTopicItems(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            List<TopicItem> topics = new ArrayList<>();
            for (JsonNode item : root) {
                addTopic(
                        topics,
                        item.path("topic").asText(""),
                        item.path("role").asText("")
                );
            }
            return topics;
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private static List<TopicItem> parseSemanticTopicItems(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            List<TopicItem> topics = new ArrayList<>();
            JsonNode active = root.path("activeTopics");
            if (active.isArray()) {
                for (JsonNode item : active) {
                    addTopic(topics, item.path("topic").asText(""), item.path("role").asText(""));
                }
            }
            JsonNode comparison = root.path("comparisonTopics");
            if (comparison.isArray()) {
                for (JsonNode item : comparison) {
                    addTopic(topics, item.path("topic").asText(""), item.path("role").asText(""));
                }
            }
            return topics;
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private static List<String> topicsFromJson(String json) {
        return parseTopicItems(json).stream().map(TopicItem::topic).toList();
    }

    private static List<String> comparisonTopicsFromJson(String json) {
        return comparisonTopics(parseTopicItems(json)).stream().map(TopicItem::topic).toList();
    }

    private static List<TopicItem> comparisonTopics(List<TopicItem> topics) {
        List<TopicItem> comparison = new ArrayList<>();
        addRoleTopic(comparison, topics, "former");
        addRoleTopic(comparison, topics, "latter");
        return comparison;
    }

    private static void addRoleTopic(List<TopicItem> target, List<TopicItem> source, String role) {
        for (TopicItem item : source) {
            if (role.equals(item.role())) {
                addTopic(target, item.topic(), role);
                return;
            }
        }
    }

    private static List<Map<String, String>> toJsonItems(List<TopicItem> topics) {
        List<Map<String, String>> items = new ArrayList<>();
        for (TopicItem item : topics) {
            Map<String, String> value = new LinkedHashMap<>();
            value.put("topic", item.topic());
            if (StringUtils.hasText(item.role())) {
                value.put("role", item.role());
            }
            items.add(value);
        }
        return items;
    }

    private static void addTopic(List<TopicItem> topics, String rawTopic, String role) {
        String topic = trimToEmpty(rawTopic);
        if (!StringUtils.hasText(topic)) {
            return;
        }
        String normalizedRole = normalizeRole(role);
        for (int index = 0; index < topics.size(); index++) {
            TopicItem existing = topics.get(index);
            if (existing.topic().equals(topic)) {
                if (StringUtils.hasText(normalizedRole) && !StringUtils.hasText(existing.role())) {
                    topics.set(index, new TopicItem(topic, normalizedRole));
                }
                return;
            }
        }
        topics.add(new TopicItem(topic, normalizedRole));
    }

    private static String roleOf(List<TopicItem> topics, String rawTopic) {
        String topic = trimToEmpty(rawTopic);
        if (!StringUtils.hasText(topic)) {
            return "";
        }
        return topics.stream()
                .filter(item -> topic.equals(item.topic()))
                .map(TopicItem::role)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private static String normalizeRole(String role) {
        String value = trimToEmpty(role);
        return "former".equals(value) || "latter".equals(value) ? value : "";
    }

    private static String writeJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"version\":\"" + VERSION + "\"}";
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record TopicItem(String topic, String role) {
    }
}
