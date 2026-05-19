package org.ysu.ckqaback.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.course.dto.CourseQueryRequest;
import org.ysu.ckqaback.qa.dto.QaModeRecommendationRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 学生端缓存键构造器。
 * <p>键名只保留必要主体信息，其余上下文统一哈希，避免把原问题写入 Redis key。</p>
 */
@Component
@RequiredArgsConstructor
public class StudentCacheKeyFactory {

    private final StudentRedisCacheProperties properties;

    public String coursesKey(String userCode, CourseQueryRequest request) {
        String params = "page=" + value(request == null ? null : request.getPage())
                + "|size=" + value(request == null ? null : request.getSize())
                + "|keyword=" + value(request == null ? null : request.getKeyword())
                + "|status=" + value(request == null ? null : request.getStatus());
        return prefix() + ":courses:user:" + safeSegment(userCode) + ":" + sha256(params);
    }

    public String courseKnowledgeBasesKey(String userCode, String courseId) {
        return prefix() + ":course-kbs:user:" + safeSegment(userCode) + ":course:" + safeSegment(courseId);
    }

    public String routingKey(Long userId, QaModeRecommendationRequest request, boolean hasConversationContext) {
        String scope = "sessionId=" + value(request == null ? null : request.getSessionId())
                + "|courseId=" + value(request == null ? null : request.getCourseId())
                + "|knowledgeBaseId=" + value(request == null ? null : request.getKnowledgeBaseId())
                + "|question=" + value(request == null ? null : request.getQuestion())
                + "|betaHybridEnabled=" + value(request == null ? null : request.getBetaHybridEnabled())
                + "|hasConversationContext=" + hasConversationContext;
        return prefix() + ":qa-routing:user:" + safeSegment(userId == null ? null : String.valueOf(userId)) + ":" + sha256(scope);
    }

    public String hybridReadinessKey(Long knowledgeBaseId, Long activeIndexRunId, String dataDirUri) {
        return prefix() + ":hybrid-readiness:kb:" + safeSegment(knowledgeBaseId == null ? null : String.valueOf(knowledgeBaseId))
                + ":index:" + safeSegment(activeIndexRunId == null ? null : String.valueOf(activeIndexRunId))
                + ":" + sha256(value(dataDirUri));
    }

    public String coursesPattern() {
        return prefix() + ":courses:*";
    }

    public String courseKnowledgeBasesPattern() {
        return prefix() + ":course-kbs:*";
    }

    public String hybridReadinessPattern(Long knowledgeBaseId) {
        return prefix() + ":hybrid-readiness:kb:" + safeSegment(knowledgeBaseId == null ? null : String.valueOf(knowledgeBaseId)) + ":*";
    }

    private String prefix() {
        String rawPrefix = StringUtils.hasText(properties.getPrefix())
                ? properties.getPrefix().trim()
                : "ckqa:student-cache:v1";
        while (rawPrefix.endsWith(":")) {
            rawPrefix = rawPrefix.substring(0, rawPrefix.length() - 1);
        }
        return rawPrefix;
    }

    private String safeSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "none";
        }
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
