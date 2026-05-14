package org.ysu.ckqaback.index;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 提示词自动调优后台执行线程池。
 * <p>
 * core=1 / max=2，避免对同一台 LLM 通道并发发起 prompt-tune；
 * 队列容量 8，超出由调用方降级为 409。
 */
@Configuration
public class PromptTuneAsyncConfig {

    @Bean(name = "promptTuneExecutor")
    public Executor promptTuneExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("prompt-tune-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(8);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
