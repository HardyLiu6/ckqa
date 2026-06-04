package org.ysu.ckqaback.qa.dto;

import lombok.Getter;

/**
 * 会话分支创建响应。
 */
@Getter
public class QaSessionForkResponse {

    private final Long parentSessionId;
    private final QaSessionResponse childSession;
    private final Long forkedFromMessageId;
    private final Integer forkedFromSequenceNo;
    private final int copiedMessageCount;

    private QaSessionForkResponse(
            Long parentSessionId,
            QaSessionResponse childSession,
            Long forkedFromMessageId,
            Integer forkedFromSequenceNo,
            int copiedMessageCount
    ) {
        this.parentSessionId = parentSessionId;
        this.childSession = childSession;
        this.forkedFromMessageId = forkedFromMessageId;
        this.forkedFromSequenceNo = forkedFromSequenceNo;
        this.copiedMessageCount = copiedMessageCount;
    }

    public static QaSessionForkResponse of(
            Long parentSessionId,
            QaSessionResponse childSession,
            Long forkedFromMessageId,
            Integer forkedFromSequenceNo,
            int copiedMessageCount
    ) {
        return new QaSessionForkResponse(
                parentSessionId,
                childSession,
                forkedFromMessageId,
                forkedFromSequenceNo,
                copiedMessageCount
        );
    }
}
