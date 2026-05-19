package org.ysu.ckqaback.course.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    private KnowledgeBaseSummaryResponse(
            @JsonProperty("id") Long id,
            @JsonProperty("kbCode") String kbCode,
            @JsonProperty("name") String name,
            @JsonProperty("status") String status,
            @JsonProperty("activeIndexRunId") Long activeIndexRunId
    ) {
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
