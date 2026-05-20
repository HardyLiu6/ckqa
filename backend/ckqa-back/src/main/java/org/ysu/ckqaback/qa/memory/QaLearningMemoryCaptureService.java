package org.ysu.ckqaback.qa.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.entity.QaLearningMemories;
import org.ysu.ckqaback.entity.QaMemoryPreferences;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.service.QaLearningMemoriesService;
import org.ysu.ckqaback.service.QaMemoryPreferencesService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 从成功问答中沉淀跨 session 学习记忆。
 *
 * <p>首版仅使用规则，不把 assistant 回答当作课程事实；assistant 文本只用于过滤明显失败回答。</p>
 */
@Service
@RequiredArgsConstructor
public class QaLearningMemoryCaptureService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_ACTIVE_MEMORIES_PER_SCOPE = 20;
    private static final int MAX_LOOKUP_MEMORIES = 100;
    private static final int MAX_TOPIC_CHARS = 48;
    private static final int MAX_MEMORY_TEXT_CHARS = 120;

    private final QaMemoryPreferencesService preferencesService;
    private final QaLearningMemoriesService memoriesService;
    private final QaSessionsService sessionsService;

    public void captureAfterAssistantSuccess(QaRetrievalLogs task, QaMessages assistantMessage) {
        if (task == null || task.getSessionId() == null || assistantMessage == null) {
            return;
        }
        QaSessions session = sessionsService.getById(task.getSessionId());
        if (!isEligibleSession(session)) {
            return;
        }
        QaMemoryPreferences preference = preferencesService.findByScope(
                session.getUserId(),
                session.getCourseId(),
                session.getKnowledgeBaseId(),
                session.getIndexRunId()
        );
        if (preference == null || !Boolean.TRUE.equals(preference.getEnabled())) {
            return;
        }
        String question = firstText(task.getOriginalQueryText(), task.getQueryText(), task.getRetrievalQueryText());
        String answer = firstText(assistantMessage.getContentText(), assistantMessage.getContent());
        if (!StringUtils.hasText(question) || isFailureAnswer(answer)) {
            return;
        }

        List<MemoryCandidate> candidates = extractCandidates(question);
        if (candidates.isEmpty()) {
            return;
        }

        List<QaLearningMemories> active = new ArrayList<>(memoriesService.listActiveByScope(
                session.getUserId(),
                session.getCourseId(),
                session.getKnowledgeBaseId(),
                session.getIndexRunId(),
                MAX_LOOKUP_MEMORIES
        ));
        Map<String, QaLearningMemories> existingByKey = new LinkedHashMap<>();
        for (QaLearningMemories memory : active) {
            if (memory != null && isActive(memory)) {
                existingByKey.put(memoryKey(memory.getMemoryType(), memory.getMemoryText()), memory);
            }
        }

        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        for (MemoryCandidate candidate : candidates) {
            String key = memoryKey(candidate.memoryType(), candidate.memoryText());
            QaLearningMemories existing = existingByKey.get(key);
            if (existing == null) {
                QaLearningMemories created = newMemory(session, assistantMessage, candidate, now);
                memoriesService.save(created);
                active.add(created);
                existingByKey.put(key, created);
            } else {
                existing.setSourceSessionId(session.getId());
                existing.setSourceMessageId(assistantMessage.getId());
                existing.setUpdatedAt(now);
                memoriesService.updateById(existing);
            }
        }

        enforceScopeLimit(active, session.getUserId());
    }

    private boolean isEligibleSession(QaSessions session) {
        return session != null
                && "formal".equals(session.getSessionType())
                && session.getUserId() != null
                && StringUtils.hasText(session.getCourseId())
                && session.getKnowledgeBaseId() != null
                && session.getIndexRunId() != null;
    }

    private List<MemoryCandidate> extractCandidates(String question) {
        List<MemoryCandidate> candidates = new ArrayList<>();
        String topic = extractTopic(question);
        if (StringUtils.hasText(topic)) {
            candidates.add(new MemoryCandidate("learning_topic", truncate("学生关注：" + topic, MAX_MEMORY_TEXT_CHARS)));
        }
        String preference = detectExplanationPreference(question);
        if (StringUtils.hasText(preference)) {
            candidates.add(new MemoryCandidate("explanation_preference", preference));
        }
        if (hasAny(question, "区别", "关系", "对比", "比较")) {
            candidates.add(new MemoryCandidate("unresolved_focus", "持续关注概念关系与对比"));
        }
        return dedupeCandidates(candidates);
    }

    private List<MemoryCandidate> dedupeCandidates(List<MemoryCandidate> candidates) {
        Map<String, MemoryCandidate> deduped = new LinkedHashMap<>();
        for (MemoryCandidate candidate : candidates) {
            deduped.putIfAbsent(memoryKey(candidate.memoryType(), candidate.memoryText()), candidate);
        }
        return List.copyOf(deduped.values());
    }

    private String extractTopic(String question) {
        String normalized = cleanupQuestion(question);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        String topic = normalized;
        topic = topic.replaceFirst("^请?(简单|通俗|详细)?(说明|解释|讲解|介绍)", "");
        topic = topic.replaceFirst("^什么是", "");
        topic = topic.replaceFirst("是什么$", "");
        int separator = firstSeparatorIndex(topic);
        if (separator >= 0) {
            topic = topic.substring(0, separator);
        }
        topic = topic.replaceAll("^(这个|它|该|上述|上面那个)", "");
        topic = truncate(topic.trim(), MAX_TOPIC_CHARS);
        return StringUtils.hasText(topic) ? topic : truncate(normalized, MAX_TOPIC_CHARS);
    }

    private String cleanupQuestion(String question) {
        return String.valueOf(question == null ? "" : question)
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("[？?！!。；;：:，,、]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int firstSeparatorIndex(String text) {
        int result = -1;
        for (String marker : List.of(" 请", " 用", " 并", " 和", " 与", " 以及")) {
            int index = text.indexOf(marker);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private String detectExplanationPreference(String question) {
        if (hasAny(question, "步骤", "流程", "过程", "一步", "怎么", "如何")) {
            return "偏好步骤化解释";
        }
        if (hasAny(question, "举例", "例子", "案例", "实例")) {
            return "偏好结合例子解释";
        }
        if (hasAny(question, "通俗", "白话", "简单", "容易懂")) {
            return "偏好通俗解释";
        }
        return "";
    }

    private boolean hasAny(String text, String... markers) {
        String value = String.valueOf(text == null ? "" : text);
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFailureAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return true;
        }
        String normalized = answer.toLowerCase(Locale.ROOT);
        return normalized.contains("无法回答")
                || normalized.contains("未找到")
                || normalized.contains("没有找到")
                || normalized.contains("系统错误")
                || normalized.contains("任务失败")
                || normalized.contains("暂时无法")
                || normalized.contains("知识库中没有")
                || normalized.contains("抱歉");
    }

    private void enforceScopeLimit(List<QaLearningMemories> active, Long userId) {
        List<QaLearningMemories> sorted = active.stream()
                .filter(Objects::nonNull)
                .filter(this::isActive)
                .filter(memory -> memory.getId() != null)
                .sorted(Comparator
                        .comparing(QaLearningMemories::getUpdatedAt, Comparator.nullsFirst(LocalDateTime::compareTo))
                        .thenComparing(QaLearningMemories::getCreatedAt, Comparator.nullsFirst(LocalDateTime::compareTo))
                        .thenComparing(QaLearningMemories::getId, Comparator.nullsFirst(Long::compareTo)))
                .toList();
        int overflow = sorted.size() - MAX_ACTIVE_MEMORIES_PER_SCOPE;
        for (int index = 0; index < overflow; index += 1) {
            memoriesService.softDeleteForUser(sorted.get(index).getId(), userId);
        }
    }

    private QaLearningMemories newMemory(QaSessions session, QaMessages assistantMessage, MemoryCandidate candidate, LocalDateTime now) {
        QaLearningMemories memory = new QaLearningMemories();
        memory.setUserId(session.getUserId());
        memory.setCourseId(session.getCourseId());
        memory.setKnowledgeBaseId(session.getKnowledgeBaseId());
        memory.setIndexRunId(session.getIndexRunId());
        memory.setMemoryType(candidate.memoryType());
        memory.setMemoryText(candidate.memoryText());
        memory.setSourceSessionId(session.getId());
        memory.setSourceMessageId(assistantMessage.getId());
        memory.setStatus("active");
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        return memory;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isActive(QaLearningMemories memory) {
        return memory != null && "active".equals(memory.getStatus());
    }

    private String memoryKey(String memoryType, String memoryText) {
        return String.valueOf(memoryType == null ? "" : memoryType).trim().toLowerCase(Locale.ROOT)
                + "::"
                + normalizeMemoryText(memoryText);
    }

    private String normalizeMemoryText(String value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int maxChars) {
        String text = String.valueOf(value == null ? "" : value).trim();
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private record MemoryCandidate(String memoryType, String memoryText) {
    }
}
