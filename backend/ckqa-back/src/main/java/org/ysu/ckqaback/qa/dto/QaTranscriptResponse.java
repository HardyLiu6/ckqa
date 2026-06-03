package org.ysu.ckqaback.qa.dto;

import lombok.Getter;

import java.util.Comparator;
import java.util.List;

/**
 * 会话完整 transcript 响应。
 */
@Getter
public class QaTranscriptResponse {

    private final QaSessionResponse session;
    private final List<QaTranscriptMessageResponse> messages;
    private final QaTranscriptSummaryResponse latestSummary;
    private final String transcriptVersion;
    private final int messageCount;
    private final Integer maxSequenceNo;

    private QaTranscriptResponse(
            QaSessionResponse session,
            List<QaTranscriptMessageResponse> messages,
            QaTranscriptSummaryResponse latestSummary,
            String transcriptVersion
    ) {
        this.session = session;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.latestSummary = latestSummary;
        this.transcriptVersion = transcriptVersion == null ? "v1" : transcriptVersion;
        this.messageCount = this.messages.size();
        this.maxSequenceNo = this.messages.stream()
                .map(QaTranscriptMessageResponse::getSequenceNo)
                .filter(sequenceNo -> sequenceNo != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    public static QaTranscriptResponse of(
            QaSessionResponse session,
            List<QaTranscriptMessageResponse> messages,
            QaTranscriptSummaryResponse latestSummary,
            String transcriptVersion
    ) {
        return new QaTranscriptResponse(session, messages, latestSummary, transcriptVersion);
    }
}
