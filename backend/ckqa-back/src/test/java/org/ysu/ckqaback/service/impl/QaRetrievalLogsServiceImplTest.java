package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaRetrievalLogs;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class QaRetrievalLogsServiceImplTest {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void recoverStaleActiveTasksShouldMarkOldPendingAndRunningTasksByMode() {
        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        List<MarkedFailure> markedFailures = new ArrayList<>();
        QaRetrievalLogsServiceImpl service = new QaRetrievalLogsServiceImpl() {
            @Override
            public List<QaRetrievalLogs> list(Wrapper<QaRetrievalLogs> queryWrapper) {
                return List.of(
                        task(1L, "running", "local", now.minusSeconds(40), "local logs"),
                        task(2L, "running", "drift", now.minusSeconds(40), "drift logs"),
                        task(3L, "running", "drift", now.minusMinutes(40), "old drift logs"),
                        task(4L, "pending", "basic", now.minusSeconds(40), "basic logs"),
                        task(5L, "success", "local", now.minusHours(2), "success logs")
                );
            }

            @Override
            public void markFailed(Long taskId, String taskStatus, String errorMessage, String latestLogs) {
                markedFailures.add(new MarkedFailure(taskId, taskStatus, errorMessage, latestLogs));
            }
        };

        Function<String, Duration> thresholdResolver = mode ->
                "drift".equals(mode) ? Duration.ofMinutes(30) : Duration.ofSeconds(30);
        Function<String, String> messageResolver = mode -> mode + " stale recovered";

        List<QaRetrievalLogs> recovered = service.recoverStaleActiveTasks(thresholdResolver, messageResolver);

        assertThat(recovered).extracting(QaRetrievalLogs::getId).containsExactly(1L, 3L, 4L);
        assertThat(markedFailures).extracting(MarkedFailure::taskId).containsExactly(1L, 3L, 4L);
        assertThat(markedFailures).extracting(MarkedFailure::taskStatus).containsOnly("stale");
        assertThat(markedFailures).extracting(MarkedFailure::errorMessage)
                .containsExactly("local stale recovered", "drift stale recovered", "basic stale recovered");
    }

    private QaRetrievalLogs task(Long id, String status, String mode, LocalDateTime heartbeatAt, String latestLogs) {
        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(id);
        task.setTaskStatus(status);
        task.setQueryMode(mode);
        task.setLastHeartbeatAt(heartbeatAt);
        task.setCreatedAt(heartbeatAt);
        task.setLatestLogs(latestLogs);
        return task;
    }

    private record MarkedFailure(Long taskId, String taskStatus, String errorMessage, String latestLogs) {
    }
}
