package org.ysu.ckqaback.qa.dto;

import lombok.Getter;

/**
 * 单轮问答响应体。
 */
@Getter
public class QaRoundResponse {

    private final QaMessageResponse userMessage;
    private final QaMessageResponse assistantMessage;
    private final String retrievalStatus;

    private QaRoundResponse(QaMessageResponse userMessage, QaMessageResponse assistantMessage, String retrievalStatus) {
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
        this.retrievalStatus = retrievalStatus;
    }

    public static QaRoundResponse of(
            QaMessageResponse userMessage,
            QaMessageResponse assistantMessage,
            String retrievalStatus
    ) {
        return new QaRoundResponse(userMessage, assistantMessage, retrievalStatus);
    }
}
