package org.ysu.ckqaback.course.dto;

import lombok.Getter;
import org.ysu.ckqaback.entity.KnowledgeBases;

/**
 * 课程下知识库摘要响应体。
 */
@Getter
public class KnowledgeBaseSummaryResponse {

    private final Long id;
    private final String kbCode;
    private final String name;
    private final String status;
    private final Long activeIndexRunId;

    private KnowledgeBaseSummaryResponse(Long id, String kbCode, String name, String status, Long activeIndexRunId) {
        this.id = id;
        this.kbCode = kbCode;
        this.name = name;
        this.status = status;
        this.activeIndexRunId = activeIndexRunId;
    }

    public static KnowledgeBaseSummaryResponse of(Long id, String kbCode, String name, String status, Long activeIndexRunId) {
        return new KnowledgeBaseSummaryResponse(id, kbCode, name, status, activeIndexRunId);
    }

    public static KnowledgeBaseSummaryResponse fromEntity(KnowledgeBases knowledgeBase) {
        return of(
                knowledgeBase.getId(),
                knowledgeBase.getKbCode(),
                knowledgeBase.getName(),
                knowledgeBase.getStatus(),
                knowledgeBase.getActiveIndexRunId()
        );
    }
}
