package org.ysu.ckqaback.pdf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * PDF 解析后台任务与事件推送线程池。
 */
@Configuration
public class PdfParseAsyncConfig {

    @Bean(name = "pdfParseExecutor")
    public Executor pdfParseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("pdf-parse-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(32);
        executor.initialize();
        return executor;
    }

    @Bean(name = "pdfParseEventScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService pdfParseEventScheduler() {
        return Executors.newScheduledThreadPool(2, new CustomizableThreadFactory("pdf-parse-events-"));
    }
}
