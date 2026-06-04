package org.ysu.ckqaback.qa.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 当前页问答会话的消息数聚合结果。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QaSessionMessageCount {

    private Long sessionId;

    private Long messageCount;
}
