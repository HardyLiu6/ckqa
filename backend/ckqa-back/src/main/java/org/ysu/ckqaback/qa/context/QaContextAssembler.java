package org.ysu.ckqaback.qa.context;

import org.ysu.ckqaback.entity.QaMessages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 问答上下文组装器：只生成 Java 侧快照，不把完整上下文传给 Python。
 */
public class QaContextAssembler {

    private final QaTopicResolver topicResolver = new QaTopicResolver();

    public QaContextAssembly assemble(String mode, String question, List<QaMessages> history) {
        return assemble(mode, question, history, null);
    }

    public QaContextAssembly assemble(String mode, String question, List<QaMessages> history, QaContextSummary summary) {
        QaContextSummary safeSummary = summary == null ? null : summary;
        List<QaMessages> usableHistory = safeHistory(history);
        QaTopicStack topicStack = topicResolver.resolve(question, usableHistory, safeSummary);
        if (!QaContextPolicy.supportsRecentContext(mode)) {
            return safeSummary != null && safeSummary.hasText() ? summaryOnly(safeSummary, topicStack) : none(topicStack, safeSummary);
        }

        if (safeSummary == null || !safeSummary.hasText()) {
            return recentOnly(usableHistory, topicStack, null);
        }

        List<QaMessages> unsummarizedHistory = usableHistory.stream()
                .filter(message -> message.getSequenceNo() != null && message.getSequenceNo() > safeSummary.untilSequenceNo())
                .toList();
        List<QaMessages> recent = selectRecentMessages(unsummarizedHistory);
        QaContextAssembly recentAssembly = buildRecentAssembly(recent, "summary_recent", topicStack, safeSummary);
        if (!recentAssembly.contextApplied()) {
            return summaryOnly(safeSummary, topicStack);
        }
        String summaryText = truncate(trimToEmpty(safeSummary.text()), QaContextPolicy.MAX_SUMMARY_CHARS);
        String snapshot = "会话摘要：\n" + summaryText + "\n\n最近对话：\n" + recentAssembly.snapshotText();
        if (snapshot.length() > QaContextPolicy.MAX_SNAPSHOT_CHARS) {
            snapshot = snapshot.substring(0, QaContextPolicy.MAX_SNAPSHOT_CHARS);
        }
        int charCount = Math.min(summaryText.length() + recentAssembly.charCount(), QaContextPolicy.MAX_SNAPSHOT_CHARS);
        return new QaContextAssembly(
                "summary_recent",
                snapshot,
                recentAssembly.messageRange(),
                charCount,
                recentAssembly.latestTopic(),
                recentAssembly.latestTopicMessageRange(),
                recentAssembly.topicSource(),
                recentAssembly.topicConfidence(),
                recentAssembly.topicStackJson(),
                recentAssembly.semanticStateVersion(),
                recentAssembly.semanticStateJson()
        );
    }

    private QaContextAssembly recentOnly(List<QaMessages> usableHistory, QaTopicStack topicStack, QaContextSummary summary) {
        if (usableHistory.isEmpty()) {
            return none(topicStack, summary);
        }

        return buildRecentAssembly(selectRecentMessages(usableHistory), "recent", topicStack, summary);
    }

    private QaContextAssembly buildRecentAssembly(
            List<QaMessages> recent,
            String strategy,
            QaTopicStack topicStack,
            QaContextSummary summary
    ) {
        if (recent.isEmpty()) {
            return none(topicStack, summary);
        }

        StringBuilder snapshot = new StringBuilder();
        int contentChars = 0;
        for (QaMessages message : recent) {
            String content = trimToEmpty(message.getContent());
            if (content.isEmpty()) {
                continue;
            }
            int nextChars = contentChars + content.length();
            if (nextChars > QaContextPolicy.MAX_RECENT_CHARS && snapshot.length() > 0) {
                break;
            }
            if (snapshot.length() > 0) {
                snapshot.append('\n');
            }
            snapshot.append(roleLabel(message.getRole())).append('：').append(content);
            contentChars = Math.min(nextChars, QaContextPolicy.MAX_RECENT_CHARS);
            if (snapshot.length() > QaContextPolicy.MAX_SNAPSHOT_CHARS) {
                snapshot.setLength(QaContextPolicy.MAX_SNAPSHOT_CHARS);
                break;
            }
        }

        if (snapshot.isEmpty()) {
            return none(topicStack, summary);
        }

        SessionSemanticState semanticState = SessionSemanticState.from(topicStack, summary);
        return new QaContextAssembly(
                strategy,
                snapshot.toString(),
                rangeOf(recent),
                Math.min(contentChars, QaContextPolicy.MAX_RECENT_CHARS),
                topicStack.latestTopic(),
                topicStack.latestTopicMessageRange(),
                topicStack.topicSource(),
                topicStack.topicConfidence(),
                topicStack.activeTopicsJson(),
                semanticState.version(),
                semanticState.json()
        );
    }

    private QaContextAssembly summaryOnly(QaContextSummary summary, QaTopicStack topicStack) {
        String summaryText = truncate(trimToEmpty(summary.text()), QaContextPolicy.MAX_SUMMARY_CHARS);
        if (summaryText.isEmpty()) {
            return none(topicStack, summary);
        }
        String snapshot = "会话摘要：\n" + summaryText;
        SessionSemanticState semanticState = SessionSemanticState.from(topicStack, summary);
        return new QaContextAssembly(
                "summary",
                snapshot,
                "",
                summaryText.length(),
                topicStack.latestTopic(),
                topicStack.latestTopicMessageRange(),
                topicStack.topicSource(),
                topicStack.topicConfidence(),
                topicStack.activeTopicsJson(),
                semanticState.version(),
                semanticState.json()
        );
    }

    private QaContextAssembly none(QaTopicStack topicStack, QaContextSummary summary) {
        QaTopicStack stack = topicStack == null ? QaTopicStack.empty() : topicStack;
        SessionSemanticState semanticState = SessionSemanticState.from(stack, summary);
        return new QaContextAssembly(
                "none",
                "",
                "",
                0,
                stack.latestTopic(),
                stack.latestTopicMessageRange(),
                stack.topicSource(),
                stack.topicConfidence(),
                stack.activeTopicsJson(),
                semanticState.version(),
                semanticState.json()
        );
    }

    private List<QaMessages> safeHistory(List<QaMessages> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream()
                .filter(message -> "user".equals(message.getRole()) || "assistant".equals(message.getRole()))
                .sorted(Comparator.comparing(QaMessages::getSequenceNo, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<QaMessages> selectRecentMessages(List<QaMessages> history) {
        int fromIndex = Math.max(0, history.size() - QaContextPolicy.MAX_RECENT_MESSAGES);
        return new ArrayList<>(history.subList(fromIndex, history.size()));
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

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
