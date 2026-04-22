package org.ysu.ckqaback.integration.graphrag;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.IndexRunsService;

import java.time.Duration;

/**
 * 应用启动时恢复超时未完成的索引任务。
 */
@Component
@RequiredArgsConstructor
public class IndexRunStartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexRunStartupRecovery.class);

    private final IndexRunsService indexRunsService;
    private final CkqaIntegrationProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            indexRunsService.recoverStaleRunningRuns(Duration.ofSeconds(properties.getTimeout().getIndexStaleSeconds()));
        } catch (Exception exception) {
            log.warn("启动阶段执行索引陈旧任务恢复失败，将在后续建索引时继续尝试恢复: {}", exception.getMessage());
            log.debug("启动阶段索引陈旧任务恢复异常详情", exception);
        }
    }
}
