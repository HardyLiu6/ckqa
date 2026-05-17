package org.ysu.ckqaback.qa.context;

import org.springframework.util.StringUtils;
import org.ysu.ckqaback.entity.QaMessages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 1 短期上下文组装器：只生成 recent 快照，不把完整上下文传给 Python。
 */
public class QaContextAssembler {

    private static final int MAX_RECENT_MESSAGES = 6;
    private static final int MAX_RECENT_CHARS = 1800;
    private static final int MAX_SNAPSHOT_CHARS = 3500;
    private static final Pattern WHAT_IS_PATTERN = Pattern.compile("^(什么是|请解释|解释一下|介绍一下)(.+?)[？?。.!！]*$");

    public QaContextAssembly assemble(String mode, String question, List<QaMessages> history) {
        if (!"basic".equals(mode) && !"local".equals(mode)) {
            return none();
        }

        List<QaMessages> usableHistory = safeHistory(history);
        if (usableHistory.isEmpty()) {
            return none();
        }

        List<QaMessages> recent = selectRecentMessages(usableHistory);
        if (recent.isEmpty()) {
            return none();
        }

        StringBuilder snapshot = new StringBuilder();
        int contentChars = 0;
        for (QaMessages message : recent) {
            String content = trimToEmpty(message.getContent());
            if (content.isEmpty()) {
                continue;
            }
            int nextChars = contentChars + content.length();
            if (nextChars > MAX_RECENT_CHARS && snapshot.length() > 0) {
                break;
            }
            if (snapshot.length() > 0) {
                snapshot.append('\n');
            }
            snapshot.append(roleLabel(message.getRole())).append('：').append(content);
            contentChars = Math.min(nextChars, MAX_RECENT_CHARS);
            if (snapshot.length() > MAX_SNAPSHOT_CHARS) {
                snapshot.setLength(MAX_SNAPSHOT_CHARS);
                break;
            }
        }

        if (snapshot.isEmpty()) {
            return none();
        }

        Topic topic = latestCompletedTopic(usableHistory);
        return new QaContextAssembly(
                "recent",
                snapshot.toString(),
                rangeOf(recent),
                Math.min(contentChars, MAX_RECENT_CHARS),
                topic.text(),
                topic.range()
        );
    }

    private QaContextAssembly none() {
        return new QaContextAssembly("none", "", "", 0, "", "");
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

    private List<QaMessages> selectRecentMessages(List<QaMessages> history) {
        int fromIndex = Math.max(0, history.size() - MAX_RECENT_MESSAGES);
        return new ArrayList<>(history.subList(fromIndex, history.size()));
    }

    private Topic latestCompletedTopic(List<QaMessages> history) {
        for (int index = history.size() - 1; index >= 1; index--) {
            QaMessages assistant = history.get(index);
            QaMessages user = history.get(index - 1);
            if (!"assistant".equals(assistant.getRole()) || !"user".equals(user.getRole())) {
                continue;
            }
            String topic = extractTopic(user.getContent());
            if (StringUtils.hasText(topic)) {
                return new Topic(topic, rangeOf(List.of(user, assistant)));
            }
        }
        return new Topic("", "");
    }

    private String extractTopic(String content) {
        String text = trimToEmpty(content);
        if (text.isEmpty()) {
            return "";
        }
        Matcher matcher = WHAT_IS_PATTERN.matcher(text);
        if (matcher.matches()) {
            return shortenTopic(matcher.group(2));
        }
        return shortenTopic(text);
    }

    private String shortenTopic(String rawTopic) {
        String topic = trimToEmpty(rawTopic)
                .replaceAll("[？?。.!！]+$", "")
                .replaceAll("\\s+", " ");
        return topic.length() > 30 ? topic.substring(0, 30) : topic;
    }

    private String rangeOf(List<QaMessages> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        Integer first = messages.get(0).getSequenceNo();
        Integer last = messages.get(messages.size() - 1).getSequenceNo();
        if (first == null || last == null) {
            return "";
        }
        return first.equals(last) ? String.valueOf(first) : first + "-" + last;
    }

    private String roleLabel(String role) {
        return "assistant".equals(role) ? "助手" : "学生";
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record Topic(String text, String range) {
    }
}
