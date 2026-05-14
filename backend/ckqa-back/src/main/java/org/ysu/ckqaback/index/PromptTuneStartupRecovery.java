package org.ysu.ckqaback.index;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.PromptTuneRunsService;

import java.time.Duration;

/**
 * 启动时把停留在 running 状态过久的提示词调优任务标记为 failed。
 */
@Component
@RequiredArgsConstructor
public class PromptTuneStartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PromptTuneStartupRecovery.class);

    private final PromptTuneRunsService promptTuneRunsService;
    private final CkqaIntegrationProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            var recovered = promptTuneRunsService.recoverStaleRunningRuns(
                    Duration.ofSeconds(properties.getTimeout().getPromptTuneStaleSeconds())
            );
            if (!recovered.isEmpty()) {
                log.info("启动阶段恢复了 {} 个陈旧的 prompt-tune 任务", recovered.size());
            }
        } catch (Exception exception) {
            log.warn("启动阶段执行 prompt-tune 陈旧任务恢复失败：{}", exception.getMessage());
            log.debug("启动阶段 prompt-tune 陈旧任务恢复异常详情", exception);
        }
    }
}
