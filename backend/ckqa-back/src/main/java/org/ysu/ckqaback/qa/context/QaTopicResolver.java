package org.ysu.ckqaback.qa.context;

import org.springframework.util.StringUtils;
import org.ysu.ckqaback.entity.QaMessages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可用语义主题栈 v1：只做低风险规则解析，不绑定 KG，也不调用在线 LLM。
 */
public class QaTopicResolver {

    private static final Pattern WHAT_IS_PREFIX_PATTERN = Pattern.compile("^(什么是|请解释|解释一下|介绍一下)(.+?)[？?。.!！]*$");
    private static final Pattern WHAT_IS_SUFFIX_PATTERN = Pattern.compile("^(.+?)(是什么|是啥|是什么概念)[？?。.!！]*$");
    private static final Pattern THEN_TOPIC_PATTERN = Pattern.compile("^(?:那|那么)?\\s*(.+?)\\s*呢[？?。.!！]*$");
    private static final Pattern COMPARISON_PATTERN = Pattern.compile("^(.+?)(?:和|与)(.+?)(?:有什么区别|有何区别|区别是什么)[？?。.!！]*$");
    private static final Pattern SUMMARY_OBJECT_PATTERN = Pattern.compile("\\{[^}]*}");
    private static final Pattern SUMMARY_TOPIC_PATTERN = Pattern.compile("\"topic\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SUMMARY_ROLE_PATTERN = Pattern.compile("\"role\"\\s*:\\s*\"([^\"]+)\"");

    public QaTopicStack resolve(String question, List<QaMessages> history, QaContextSummary summary) {
        TopicState state = fromSummary(summary);
        state = mergeHistory(state, history);
        return resolveQuestion(trimToEmpty(question), state);
    }

    public QaTopicStack resolveFromHistory(List<QaMessages> history, QaContextSummary summary) {
        return resolve("", history, summary);
    }

    private TopicState fromSummary(QaContextSummary summary) {
        TopicState state = new TopicState();
        if (summary == null) {
            return state;
        }
        SummaryTopics summaryTopics = parseSummaryTopics(summary.activeTopicsJson());
        state.activeTopics.addAll(summaryTopics.activeTopics());
        state.comparisonTopics.addAll(summaryTopics.comparisonTopics());
        if (StringUtils.hasText(summary.latestTopic())) {
            state.latestTopic = summary.latestTopic().trim();
            state.latestRange = trimToEmpty(summary.latestTopicMessageRange());
            state.source = "summary";
            state.confidence = 0.75D;
            addTopic(state.activeTopics, state.latestTopic);
        }
        return state;
    }

    private TopicState mergeHistory(TopicState state, List<QaMessages> history) {
        for (QaMessages message : safeHistory(history)) {
            if (!"user".equals(message.getRole())) {
                continue;
            }
            ResolvedQuestion resolved = extractTopic(message.getContent(), state);
            if (resolved == null) {
                continue;
            }
            state.latestTopic = resolved.latestTopic();
            state.latestRange = rangeForUserMessage(message, history);
            state.source = historySource(resolved.source());
            state.confidence = resolved.confidence();
            if (resolved.resetsComparisonTopics()) {
                state.comparisonTopics.clear();
            }
            if (!resolved.comparisonTopics().isEmpty()) {
                state.comparisonTopics.clear();
                state.comparisonTopics.addAll(resolved.comparisonTopics());
            }
            if (resolved.activeTopics().isEmpty()) {
                addTopic(state.activeTopics, resolved.latestTopic());
            } else {
                resolved.activeTopics().forEach(topic -> addTopic(state.activeTopics, topic));
            }
        }
        return state;
    }

    private QaTopicStack resolveQuestion(String question, TopicState state) {
        ResolvedQuestion current = extractTopic(question, state);
        if (current != null) {
            List<String> active = new ArrayList<>(state.activeTopics);
            if (current.activeTopics().isEmpty()) {
                addTopic(active, current.latestTopic());
            } else {
                current.activeTopics().forEach(topic -> addTopic(active, topic));
            }
            String range = current.useLatestRange() ? state.latestRange : "";
            List<String> comparisonTopics = current.comparisonTopics().isEmpty()
                    ? state.comparisonTopics
                    : current.comparisonTopics();
            return QaTopicStack.of(current.latestTopic(), range, current.source(), current.confidence(), active, comparisonTopics);
        }
        if (containsFormer(question) && !state.activeTopics.isEmpty()) {
            if (state.activeTopics.size() == 2) {
                return QaTopicStack.of(state.activeTopics.get(0), state.latestRange, "comparison_pronoun", 0.86D, state.activeTopics, state.activeTopics);
            }
            return QaTopicStack.empty();
        }
        if (containsLatter(question) && state.activeTopics.size() >= 2) {
            if (state.activeTopics.size() == 2) {
                return QaTopicStack.of(state.activeTopics.get(1), state.latestRange, "comparison_pronoun", 0.86D, state.activeTopics, state.activeTopics);
            }
            return QaTopicStack.empty();
        }
        if (QaContextPolicy.isPronounFollowUp(question) && StringUtils.hasText(state.latestTopic)) {
            return QaTopicStack.of(state.latestTopic, state.latestRange, state.source, state.confidence, state.activeTopics, state.comparisonTopics);
        }
        if (StringUtils.hasText(state.latestTopic)) {
            return QaTopicStack.of(state.latestTopic, state.latestRange, state.source, state.confidence, state.activeTopics, state.comparisonTopics);
        }
        return QaTopicStack.empty();
    }

    private ResolvedQuestion extractTopic(String content, TopicState state) {
        String text = normalizeQuestion(content);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher comparisonMatcher = COMPARISON_PATTERN.matcher(text);
        if (comparisonMatcher.matches()) {
            String first = shortenTopic(comparisonMatcher.group(1));
            String second = shortenTopic(comparisonMatcher.group(2));
            if (StringUtils.hasText(first) && StringUtils.hasText(second)) {
                return new ResolvedQuestion(second, "comparison", 0.92D, List.of(first, second), List.of(first, second), false, false);
            }
        }
        if (containsFormer(text) && hasComparisonPair(state)) {
            return comparisonPronoun(state.comparisonTopics.get(0), state.comparisonTopics);
        }
        if (containsLatter(text) && hasComparisonPair(state)) {
            return comparisonPronoun(state.comparisonTopics.get(1), state.comparisonTopics);
        }
        Matcher prefixMatcher = WHAT_IS_PREFIX_PATTERN.matcher(text);
        if (prefixMatcher.matches()) {
            return singleTopic(prefixMatcher.group(2), "explicit", 0.95D);
        }
        Matcher suffixMatcher = WHAT_IS_SUFFIX_PATTERN.matcher(text);
        if (suffixMatcher.matches()) {
            return singleTopic(suffixMatcher.group(1), "explicit", 0.95D);
        }
        Matcher thenMatcher = THEN_TOPIC_PATTERN.matcher(text);
        if (thenMatcher.matches() && !isPronounFollowUp(text)) {
            return singleTopic(thenMatcher.group(1), "explicit_follow_up", 0.9D);
        }
        if (isPronounFollowUp(text) && StringUtils.hasText(state.latestTopic)) {
            return new ResolvedQuestion(
                    state.latestTopic,
                    StringUtils.hasText(state.source) ? state.source : "history",
                    state.confidence,
                    new ArrayList<>(state.activeTopics),
                    List.of(),
                    false,
                    true
            );
        }
        return null;
    }

    private ResolvedQuestion singleTopic(String rawTopic, String source, double confidence) {
        String topic = shortenTopic(rawTopic);
        return StringUtils.hasText(topic) ? new ResolvedQuestion(topic, source, confidence, List.of(topic), List.of(), true, false) : null;
    }

    private List<QaMessages> safeHistory(List<QaMessages> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream()
                .filter(message -> "user".equals(message.getRole()) || "assistant".equals(message.getRole()))
                .sorted(Comparator.comparing(QaMessages::getSequenceNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private String rangeForUserMessage(QaMessages userMessage, List<QaMessages> history) {
        if (userMessage == null || userMessage.getSequenceNo() == null) {
            return "";
        }
        Integer last = safeHistory(history).stream()
                .filter(message -> "assistant".equals(message.getRole()))
                .map(QaMessages::getSequenceNo)
                .filter(sequenceNo -> sequenceNo != null && sequenceNo > userMessage.getSequenceNo())
                .findFirst()
                .orElse(userMessage.getSequenceNo());
        return userMessage.getSequenceNo().equals(last) ? String.valueOf(userMessage.getSequenceNo()) : userMessage.getSequenceNo() + "-" + last;
    }

    private SummaryTopics parseSummaryTopics(String activeTopicsJson) {
        if (!StringUtils.hasText(activeTopicsJson)) {
            return new SummaryTopics(List.of(), List.of());
        }
        List<String> topics = new ArrayList<>();
        List<String> comparisonTopics = new ArrayList<>();
        Matcher objectMatcher = SUMMARY_OBJECT_PATTERN.matcher(activeTopicsJson);
        while (objectMatcher.find()) {
            String object = objectMatcher.group();
            String topic = matchFirst(SUMMARY_TOPIC_PATTERN, object);
            addTopic(topics, topic);
            String role = matchFirst(SUMMARY_ROLE_PATTERN, object);
            if ("former".equals(role)) {
                ensureComparisonSlot(comparisonTopics, 0, topic);
            } else if ("latter".equals(role)) {
                ensureComparisonSlot(comparisonTopics, 1, topic);
            }
        }
        if (comparisonTopics.size() < 2 || !StringUtils.hasText(comparisonTopics.get(0)) || !StringUtils.hasText(comparisonTopics.get(1))) {
            comparisonTopics = List.of();
        }
        return new SummaryTopics(topics, comparisonTopics);
    }

    private boolean containsFormer(String question) {
        return StringUtils.hasText(question) && question.contains("前者");
    }

    private boolean containsLatter(String question) {
        return StringUtils.hasText(question) && question.contains("后者");
    }

    private boolean isPronounFollowUp(String question) {
        return QaContextPolicy.isPronounFollowUp(question) || (StringUtils.hasText(question) && question.contains("那个"));
    }

    private boolean hasComparisonPair(TopicState state) {
        return state != null && state.comparisonTopics.size() >= 2;
    }

    private ResolvedQuestion comparisonPronoun(String topic, List<String> comparisonTopics) {
        String resolvedTopic = shortenTopic(topic);
        return StringUtils.hasText(resolvedTopic)
                ? new ResolvedQuestion(resolvedTopic, "comparison_pronoun", 0.86D, List.of(resolvedTopic), comparisonTopics, false, true)
                : null;
    }

    private String historySource(String source) {
        if ("explicit".equals(source) || "comparison".equals(source) || "explicit_follow_up".equals(source)) {
            return "history";
        }
        return StringUtils.hasText(source) ? source : "history";
    }

    private String matchFirst(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(trimToEmpty(value));
        return matcher.find() ? matcher.group(1) : "";
    }

    private void ensureComparisonSlot(List<String> topics, int index, String topic) {
        while (topics.size() <= index) {
            topics.add("");
        }
        topics.set(index, shortenTopic(topic));
    }

    private void addTopic(List<String> topics, String rawTopic) {
        String topic = shortenTopic(rawTopic);
        if (StringUtils.hasText(topic) && !topics.contains(topic)) {
            topics.add(topic);
        }
    }

    private String normalizeQuestion(String content) {
        return trimToEmpty(content).replaceAll("\\s+", " ");
    }

    private String shortenTopic(String rawTopic) {
        String topic = trimToEmpty(rawTopic)
                .replaceAll("[？?。.!！]+$", "")
                .replaceAll("\\s+", " ");
        return topic.length() > 30 ? topic.substring(0, 30) : topic;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record ResolvedQuestion(
            String latestTopic,
            String source,
            Double confidence,
            List<String> activeTopics,
            List<String> comparisonTopics,
            boolean resetsComparisonTopics,
            boolean useLatestRange
    ) {
    }

    private record SummaryTopics(List<String> activeTopics, List<String> comparisonTopics) {
    }

    private static final class TopicState {
        private final List<String> activeTopics = new ArrayList<>();
        private final List<String> comparisonTopics = new ArrayList<>();
        private String latestTopic = "";
        private String latestRange = "";
        private String source = "";
        private Double confidence = null;
    }
}
