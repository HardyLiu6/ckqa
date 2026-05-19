package org.ysu.ckqaback.qa.ops;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 问答运维反馈查询行。
 */
@Getter
@Setter
public class QaOperationFeedbackRow {
    private Long id;
    private Long messageId;
    private Long retrievalLogId;
    private Long sessionId;
    private Long userId;
    private String username;
    private String displayName;
    private String rating;
    private String tags;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
