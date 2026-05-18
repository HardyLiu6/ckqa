package org.ysu.ckqaback.index;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 04 步评分任务专用线程池。
 *
 * <p>串行跑（corePoolSize=1）：避免多 build run 同时打 LLM 服务导致限流。
 * queueCapacity=10 允许排队，超过则拒绝（前端会拿到 5xx）。</p>
 */
@Configuration
public class ExtractionEvalAsyncConfig {

    @Bean(name = "extractionEvalExecutor")
    public Executor extractionEvalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("extraction-eval-");
        executor.setKeepAliveSeconds(0);
        executor.setAllowCoreThreadTimeOut(false);
        executor.initialize();
        return executor;
    }
}
