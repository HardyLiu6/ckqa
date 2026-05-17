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
            // 1. 先把「终态字段已写但 status 没翻」的丢失更新脏记录立即修正（不依赖阈值）。
            //    这类记录是 worker mark success/failed 后被 tailer 滞后回写覆盖产生的，
            //    必须立即清理，否则前端会一直转圈到 stale 阈值（默认 40 分钟）才返回。
            var inconsistent = promptTuneRunsService.recoverInconsistentRunningRuns();
            if (!inconsistent.isEmpty()) {
                log.info("启动阶段修正了 {} 个状态不一致的 prompt-tune 任务（finished_at 已写但 status=running）", inconsistent.size());
            }

            // 2. 再按陈旧阈值兜底标 failed：处理后端崩溃 / 进程被 kill 留下的真正 stuck 任务。
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
