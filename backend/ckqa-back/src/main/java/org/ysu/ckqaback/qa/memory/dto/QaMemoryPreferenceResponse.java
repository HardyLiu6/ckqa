package org.ysu.ckqaback.qa.memory.dto;

import lombok.Getter;

/**
 * 学生端长期记忆偏好响应。
 */
@Getter
public class QaMemoryPreferenceResponse {

    private final String courseId;
    private final Long knowledgeBaseId;
    private final Long indexRunId;
    private final Boolean enabled;

    private QaMemoryPreferenceResponse(String courseId, Long knowledgeBaseId, Long indexRunId, Boolean enabled) {
        this.courseId = courseId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.indexRunId = indexRunId;
        this.enabled = enabled;
    }

    public static QaMemoryPreferenceResponse of(String courseId, Long knowledgeBaseId, Long indexRunId, Boolean enabled) {
        return new QaMemoryPreferenceResponse(courseId, knowledgeBaseId, indexRunId, Boolean.TRUE.equals(enabled));
    }
}
