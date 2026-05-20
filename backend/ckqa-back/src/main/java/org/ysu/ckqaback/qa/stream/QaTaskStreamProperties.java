package org.ysu.ckqaback.qa.stream;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 学生端 QA 任务事件流配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ckqa.qa-stream")
public class QaTaskStreamProperties {

    private boolean enabled = true;
    private long timeoutSeconds = 300L;
    private long statusIntervalSeconds = 2L;
    private long heartbeatSeconds = 15L;
    private int deltaChars = 120;
    private int emptyAssistantRetryCount = 2;
    private long emptyAssistantRetryDelayMillis = 700L;

    public long timeoutMillis() {
        return Math.max(1L, timeoutSeconds) * 1000L;
    }

    public long statusIntervalSeconds() {
        return Math.max(1L, statusIntervalSeconds);
    }

    public long heartbeatSeconds() {
        return Math.max(1L, heartbeatSeconds);
    }

    public int deltaChars() {
        return Math.max(40, deltaChars);
    }

    public int emptyAssistantRetryCount() {
        return Math.max(0, emptyAssistantRetryCount);
    }

    public long emptyAssistantRetryDelayMillis() {
        return Math.max(100L, emptyAssistantRetryDelayMillis);
    }
}
