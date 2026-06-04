package org.ysu.ckqaback.qa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.ysu.ckqaback.entity.QaSessions;

import java.time.LocalDateTime;

/**
 * 问答会话响应体。
 */
@Getter
public class QaSessionResponse {

    private final Long id;
    private final String sessionCode;
    private final Long userId;
    private final String courseId;
    private final Long knowledgeBaseId;
    private final Long indexRunId;
    private final LocalDateTime indexLockedAt;
    private final String sessionType;
    private final Long parentSessionId;
    private final Long forkedFromMessageId;
    private final Integer forkedFromSequenceNo;
    private final String forkReason;
    private final String transcriptVersion;
    private final String title;
    private final String status;
    @JsonProperty("isFavorite")
    private final boolean isFavorite;
    private final LocalDateTime lastMessageAt;
    private final LocalDateTime createdAt;
    private final long messageCount;

    private QaSessionResponse(
            Long id,
            String sessionCode,
            Long userId,
            String courseId,
            Long knowledgeBaseId,
            Long indexRunId,
            LocalDateTime indexLockedAt,
            String sessionType,
            Long parentSessionId,
            Long forkedFromMessageId,
            Integer forkedFromSequenceNo,
            String forkReason,
            String transcriptVersion,
            String title,
            String status,
            boolean isFavorite,
            LocalDateTime lastMessageAt,
            LocalDateTime createdAt,
            long messageCount
    ) {
        this.id = id;
        this.sessionCode = sessionCode;
        this.userId = userId;
        this.courseId = courseId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.indexRunId = indexRunId;
        this.indexLockedAt = indexLockedAt;
        this.sessionType = sessionType;
        this.parentSessionId = parentSessionId;
        this.forkedFromMessageId = forkedFromMessageId;
        this.forkedFromSequenceNo = forkedFromSequenceNo;
        this.forkReason = forkReason;
        this.transcriptVersion = transcriptVersion;
        this.title = title;
        this.status = status;
        this.isFavorite = isFavorite;
        this.lastMessageAt = lastMessageAt;
        this.createdAt = createdAt;
        this.messageCount = Math.max(0L, messageCount);
    }

    public static QaSessionResponse of(
            Long id,
            String sessionCode,
            Long userId,
            String courseId,
            Long knowledgeBaseId,
            Long indexRunId,
            LocalDateTime indexLockedAt,
            String sessionType,
            String title,
            String status,
            LocalDateTime lastMessageAt,
            LocalDateTime createdAt
    ) {
        return of(
                id,
                sessionCode,
                userId,
                courseId,
                knowledgeBaseId,
                indexRunId,
                indexLockedAt,
                sessionType,
                null,
                null,
                null,
                null,
                "v1",
                title,
                status,
                false,
                lastMessageAt,
                createdAt,
                0L
        );
    }

    public static QaSessionResponse of(
            Long id,
            String sessionCode,
            Long userId,
            String courseId,
            Long knowledgeBaseId,
            Long indexRunId,
            LocalDateTime indexLockedAt,
            String sessionType,
            Long parentSessionId,
            Long forkedFromMessageId,
            Integer forkedFromSequenceNo,
            String forkReason,
            String transcriptVersion,
            String title,
            String status,
            LocalDateTime lastMessageAt,
            LocalDateTime createdAt,
            long messageCount
    ) {
        return of(
                id,
                sessionCode,
                userId,
                courseId,
                knowledgeBaseId,
                indexRunId,
                indexLockedAt,
                sessionType,
                parentSessionId,
                forkedFromMessageId,
                forkedFromSequenceNo,
                forkReason,
                transcriptVersion,
                title,
                status,
                false,
                lastMessageAt,
                createdAt,
                messageCount
        );
    }

    public static QaSessionResponse of(
            Long id,
            String sessionCode,
            Long userId,
            String courseId,
            Long knowledgeBaseId,
            Long indexRunId,
            LocalDateTime indexLockedAt,
            String sessionType,
            Long parentSessionId,
            Long forkedFromMessageId,
            Integer forkedFromSequenceNo,
            String forkReason,
            String transcriptVersion,
            String title,
            String status,
            boolean isFavorite,
            LocalDateTime lastMessageAt,
            LocalDateTime createdAt,
            long messageCount
    ) {
        return new QaSessionResponse(
                id,
                sessionCode,
                userId,
                courseId,
                knowledgeBaseId,
                indexRunId,
                indexLockedAt,
                sessionType,
                parentSessionId,
                forkedFromMessageId,
                forkedFromSequenceNo,
                forkReason,
                transcriptVersion == null ? "v1" : transcriptVersion,
                title,
                status,
                isFavorite,
                lastMessageAt,
                createdAt,
                messageCount
        );
    }

    public static QaSessionResponse of(
            Long id,
            String sessionCode,
            Long userId,
            String courseId,
            Long knowledgeBaseId,
            Long indexRunId,
            LocalDateTime indexLockedAt,
            String sessionType,
            Long parentSessionId,
            Long forkedFromMessageId,
            Integer forkedFromSequenceNo,
            String forkReason,
            String transcriptVersion,
            String title,
            String status,
            LocalDateTime lastMessageAt,
            LocalDateTime createdAt
    ) {
        return of(
                id,
                sessionCode,
                userId,
                courseId,
                knowledgeBaseId,
                indexRunId,
                indexLockedAt,
                sessionType,
                parentSessionId,
                forkedFromMessageId,
                forkedFromSequenceNo,
                forkReason,
                transcriptVersion,
                title,
                status,
                false,
                lastMessageAt,
                createdAt,
                0L
        );
    }

    public static QaSessionResponse of(
            Long id,
            String sessionCode,
            Long userId,
            String courseId,
            Long knowledgeBaseId,
            String sessionType,
            String title,
            String status,
            LocalDateTime lastMessageAt,
            LocalDateTime createdAt
    ) {
        return of(id, sessionCode, userId, courseId, knowledgeBaseId, null, null, sessionType, title, status, lastMessageAt, createdAt);
    }

    public static QaSessionResponse of(
            Long id,
            String sessionCode,
            Long userId,
            String courseId,
            Long knowledgeBaseId,
            Long indexRunId,
            LocalDateTime indexLockedAt,
            String sessionType,
            String title,
            String status,
            LocalDateTime lastMessageAt,
            LocalDateTime createdAt,
            boolean isFavorite
    ) {
        return new QaSessionResponse(
                id,
                sessionCode,
                userId,
                courseId,
                knowledgeBaseId,
                indexRunId,
                indexLockedAt,
                sessionType,
                null,
                null,
                null,
                null,
                "v1",
                title,
                status,
                isFavorite,
                lastMessageAt,
                createdAt,
                0L
        );
    }

    public static QaSessionResponse fromEntity(QaSessions session) {
        return fromEntity(session, 0L);
    }

    public static QaSessionResponse fromEntity(QaSessions session, long messageCount) {
        return of(
                session.getId(),
                session.getSessionCode(),
                session.getUserId(),
                session.getCourseId(),
                session.getKnowledgeBaseId(),
                session.getIndexRunId(),
                session.getIndexLockedAt(),
                session.getSessionType(),
                session.getParentSessionId(),
                session.getForkedFromMessageId(),
                session.getForkedFromSequenceNo(),
                session.getForkReason(),
                session.getTranscriptVersion(),
                session.getTitle(),
                session.getStatus(),
                Boolean.TRUE.equals(session.getIsFavorite()),
                session.getLastMessageAt(),
                session.getCreatedAt(),
                messageCount
        );
    }
}
