package org.ysu.ckqaback.qa.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.List;

/**
 * 本轮语义主题尝试绑定 KG 实体的诊断结果。
 */
public record QaTopicEntityBindingResult(
        Boolean applied,
        String status,
        String strategy,
        int candidateCount,
        Double topScore,
        String selectedId,
        String selectedName,
        String selectedType,
        String candidatesJson,
        String fallbackReason,
        long lookupDurationMs,
        List<QaTopicEntityBindingCandidate> candidates
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ACTIVE_NEO4J_TOPIC_MATCH = "active_neo4j_topic_match";
    private static final int SELECTED_ID_MAX = 128;
    private static final int SELECTED_NAME_MAX = 255;
    private static final int SELECTED_TYPE_MAX = 64;
    private static final int CANDIDATE_ID_MAX = 128;
    private static final int CANDIDATE_NAME_MAX = 255;
    private static final int CANDIDATE_TYPE_MAX = 64;
    private static final int CANDIDATE_HUMAN_READABLE_ID_MAX = 255;
    private static final int CANDIDATE_MATCH_REASON_MAX = 128;
    private static final int CANDIDATE_SOURCE_MAX = 64;
    private static final int FALLBACK_REASON_MAX = 500;

    public QaTopicEntityBindingResult {
        applied = Boolean.TRUE.equals(applied);
        status = hasText(status) ? status : "skipped";
        strategy = hasText(strategy) ? strategy : "none";
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        candidateCount = Math.max(0, candidateCount);
        candidatesJson = hasText(candidatesJson) ? candidatesJson : "[]";
        selectedId = truncate(selectedId, SELECTED_ID_MAX);
        selectedName = truncate(selectedName, SELECTED_NAME_MAX);
        selectedType = truncate(selectedType, SELECTED_TYPE_MAX);
        fallbackReason = truncate(fallbackReason, FALLBACK_REASON_MAX);
        lookupDurationMs = Math.max(0L, lookupDurationMs);
    }

    public static QaTopicEntityBindingResult skipped(String reason) {
        return skipped(reason, 0L);
    }

    public static QaTopicEntityBindingResult skipped(String reason, long durationMs) {
        return empty("skipped", "none", reason, durationMs);
    }

    public static QaTopicEntityBindingResult fallback(String reason, long durationMs) {
        return empty("fallback", "none", reason, durationMs);
    }

    public static QaTopicEntityBindingResult activeNeo4jFallback(String reason, long durationMs) {
        return empty("fallback", ACTIVE_NEO4J_TOPIC_MATCH, reason, durationMs);
    }

    public static QaTopicEntityBindingResult failed(String reason, long durationMs) {
        return empty("failed", ACTIVE_NEO4J_TOPIC_MATCH, reason, durationMs);
    }

    public static QaTopicEntityBindingResult success(List<QaTopicEntityBindingCandidate> candidates, long durationMs) {
        return fromCandidates(true, "success", candidates, null, durationMs);
    }

    public static QaTopicEntityBindingResult ambiguous(List<QaTopicEntityBindingCandidate> candidates, long durationMs) {
        return fromCandidates(false, "ambiguous", candidates, "ambiguous_candidates", durationMs);
    }

    private static QaTopicEntityBindingResult empty(String status, String strategy, String reason, long durationMs) {
        return new QaTopicEntityBindingResult(
                false,
                status,
                strategy,
                0,
                null,
                null,
                null,
                null,
                "[]",
                reason,
                durationMs,
                List.of()
        );
    }

    private static QaTopicEntityBindingResult fromCandidates(
            boolean applied,
            String status,
            List<QaTopicEntityBindingCandidate> rawCandidates,
            String fallbackReason,
            long durationMs
    ) {
        List<QaTopicEntityBindingCandidate> safeCandidates = rawCandidates == null
                ? List.of()
                : rawCandidates.stream()
                .map(QaTopicEntityBindingResult::sanitizeCandidate)
                .filter(Objects::nonNull)
                .toList();
        QaTopicEntityBindingCandidate selected = safeCandidates.isEmpty() ? null : safeCandidates.get(0);
        return new QaTopicEntityBindingResult(
                applied,
                status,
                ACTIVE_NEO4J_TOPIC_MATCH,
                safeCandidates.size(),
                selected == null ? null : selected.score(),
                selected == null ? null : selected.id(),
                selected == null ? null : selected.name(),
                selected == null ? null : selected.type(),
                serializeCandidates(safeCandidates),
                truncate(fallbackReason, FALLBACK_REASON_MAX),
                durationMs,
                safeCandidates
        );
    }

    private static QaTopicEntityBindingCandidate sanitizeCandidate(QaTopicEntityBindingCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        return new QaTopicEntityBindingCandidate(
                truncate(candidate.id(), CANDIDATE_ID_MAX),
                truncate(candidate.name(), CANDIDATE_NAME_MAX),
                truncate(candidate.type(), CANDIDATE_TYPE_MAX),
                truncate(candidate.humanReadableId(), CANDIDATE_HUMAN_READABLE_ID_MAX),
                candidate.score(),
                truncate(candidate.matchReason(), CANDIDATE_MATCH_REASON_MAX),
                truncate(candidate.source(), CANDIDATE_SOURCE_MAX)
        );
    }

    private static String serializeCandidates(List<QaTopicEntityBindingCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(candidates);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String truncate(String value, int maxLength) {
        if (!hasText(value)) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
