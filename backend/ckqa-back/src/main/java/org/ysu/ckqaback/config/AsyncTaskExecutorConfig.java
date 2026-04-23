package org.ysu.ckqaback.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 问答异步任务线程池配置。
 */
@Configuration
public class AsyncTaskExecutorConfig {

    @Bean(name = "qaTaskExecutor")
    public TaskExecutor qaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("qa-task-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
