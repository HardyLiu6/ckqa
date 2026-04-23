package org.ysu.ckqaback.qa;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.QaRetrievalLogsService;

import java.time.Duration;

/**
 * 应用启动时恢复超时未完成的异步问答任务。
 */
@Component
@RequiredArgsConstructor
public class QaTaskStartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QaTaskStartupRecovery.class);

    private final QaRetrievalLogsService qaRetrievalLogsService;
    private final CkqaIntegrationProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            qaRetrievalLogsService.recoverStaleActiveTasks(
                    mode -> Duration.ofSeconds(properties.resolveQueryTaskModePolicy(mode).staleTimeoutSeconds()),
                    mode -> properties.resolveQueryTaskModePolicy(mode).timeoutMessage()
            );
        } catch (Exception exception) {
            log.warn("启动阶段执行问答陈旧任务恢复失败，将在后续任务轮询时继续兜底: {}", exception.getMessage());
            log.debug("启动阶段问答陈旧任务恢复异常详情", exception);
        }
    }
}
