package org.ysu.ckqaback.qa.dto;

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
    private final String sessionType;
    private final String title;
    private final String status;
    private final LocalDateTime lastMessageAt;
    private final LocalDateTime createdAt;

    private QaSessionResponse(
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
        this.id = id;
        this.sessionCode = sessionCode;
        this.userId = userId;
        this.courseId = courseId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.sessionType = sessionType;
        this.title = title;
        this.status = status;
        this.lastMessageAt = lastMessageAt;
        this.createdAt = createdAt;
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
        return new QaSessionResponse(id, sessionCode, userId, courseId, knowledgeBaseId, sessionType, title, status, lastMessageAt, createdAt);
    }

    public static QaSessionResponse fromEntity(QaSessions session) {
        return of(
                session.getId(),
                session.getSessionCode(),
                session.getUserId(),
                session.getCourseId(),
                session.getKnowledgeBaseId(),
                session.getSessionType(),
                session.getTitle(),
                session.getStatus(),
                session.getLastMessageAt(),
                session.getCreatedAt()
        );
    }
}
