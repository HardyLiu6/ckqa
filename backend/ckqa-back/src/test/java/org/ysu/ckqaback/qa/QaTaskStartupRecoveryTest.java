package org.ysu.ckqaback.qa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.service.QaRetrievalLogsService;

import java.time.Duration;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class QaTaskStartupRecoveryTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecoverStaleQaTasksWithModeSpecificThresholdsOnStartup() {
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        properties.getTimeout().setQueryTaskStaleSeconds(300L);
        properties.getTimeout().getQueryTaskModeStaleSeconds().put("drift", 1800L);

        QaTaskStartupRecovery recovery = new QaTaskStartupRecovery(retrievalLogsService, properties);

        recovery.run(new DefaultApplicationArguments());

        org.mockito.ArgumentCaptor<Function<String, Duration>> thresholdCaptor =
                org.mockito.ArgumentCaptor.forClass(Function.class);
        org.mockito.ArgumentCaptor<Function<String, String>> messageCaptor =
                org.mockito.ArgumentCaptor.forClass(Function.class);
        then(retrievalLogsService).should().recoverStaleActiveTasks(thresholdCaptor.capture(), messageCaptor.capture());
        then(retrievalLogsService).shouldHaveNoMoreInteractions();

        assertThat(thresholdCaptor.getValue().apply("local")).isEqualTo(Duration.ofSeconds(300));
        assertThat(thresholdCaptor.getValue().apply("drift")).isEqualTo(Duration.ofSeconds(1800));
        assertThat(messageCaptor.getValue().apply("drift")).contains("drift");
        assertThat(messageCaptor.getValue().apply("local")).contains("local");
    }

    @Test
    void shouldNotFailApplicationWhenRecoveryThrows() {
        QaRetrievalLogsService retrievalLogsService = mock(QaRetrievalLogsService.class);
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        org.mockito.BDDMockito.willThrow(new IllegalStateException("db not ready"))
                .given(retrievalLogsService)
                .recoverStaleActiveTasks(any(), any());

        QaTaskStartupRecovery recovery = new QaTaskStartupRecovery(retrievalLogsService, properties);

        recovery.run(new DefaultApplicationArguments());

        then(retrievalLogsService).should().recoverStaleActiveTasks(any(), any());
    }
}
