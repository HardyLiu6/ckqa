package org.ysu.ckqaback.qa.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 问题领域校验响应。
 */
@Getter
public class QaQuestionDomainCheckResponse {

    public static final String STRATEGY = "semantic_relevance_v1";

    private final String status;
    private final String reasonCode;
    private final String message;
    private final String strategy;

    @JsonCreator
    public QaQuestionDomainCheckResponse(
            @JsonProperty("status") String status,
            @JsonProperty("reasonCode") String reasonCode,
            @JsonProperty("message") String message,
            @JsonProperty("strategy") String strategy
    ) {
        this.status = status;
        this.reasonCode = reasonCode;
        this.message = message;
        this.strategy = strategy;
    }

    public static QaQuestionDomainCheckResponse allowed() {
        return new QaQuestionDomainCheckResponse(
                "allowed",
                "course_or_uncertain",
                "问题可进入课程问答流程。",
                STRATEGY
        );
    }

    public static QaQuestionDomainCheckResponse outOfScope(String reasonCode, String message) {
        return new QaQuestionDomainCheckResponse("out_of_scope", reasonCode, message, STRATEGY);
    }
}
