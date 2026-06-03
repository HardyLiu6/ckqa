package org.ysu.ckqaback.qa.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 会话分支创建请求。
 */
@Getter
@Setter
public class ForkQaSessionRequest {

    @Positive(message = "forkedFromMessageId必须大于0")
    private Long forkedFromMessageId;

    @Positive(message = "forkedFromSequenceNo必须大于0")
    private Integer forkedFromSequenceNo;

    @Size(max = 255, message = "title长度不能超过255")
    private String title;

    @Size(max = 255, message = "forkReason长度不能超过255")
    private String forkReason;
}
