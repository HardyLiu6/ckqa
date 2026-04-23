package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QaAsyncTimeContractServiceTest {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void createSessionShouldStampShanghaiLocalCreatedAt() {
        AtomicReference<QaSessions> saved = new AtomicReference<>();
        QaSessionsServiceImpl service = new QaSessionsServiceImpl() {
            @Override
            public boolean save(QaSessions entity) {
                saved.set(entity);
                return true;
            }

            @Override
            public boolean exists(Wrapper<QaSessions> queryWrapper) {
                return false;
            }
        };

        CreateQaSessionRequest request = new CreateQaSessionRequest();
        request.setUserId(3L);
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        request.setTitle("联调会话");

        QaSessions session = service.createSession(request);

        assertThat(session.getCreatedAt()).isNotNull();
        assertThat(saved.get()).isSameAs(session);
        assertRecentShanghai(session.getCreatedAt());
    }

    @Test
    void appendUserMessageShouldStampShanghaiLocalCreatedAt() {
        AtomicReference<QaMessages> saved = new AtomicReference<>();
        QaMessagesServiceImpl service = new QaMessagesServiceImpl() {
            @Override
            public boolean save(QaMessages entity) {
                saved.set(entity);
                return true;
            }

            @Override
            public QaMessages getOne(Wrapper<QaMessages> queryWrapper, boolean throwEx) {
                return null;
            }
        };

        QaMessages message = service.appendUserMessage(5L, "请概括操作系统的目标和作用");

        assertThat(saved.get()).isSameAs(message);
        assertThat(message.getSequenceNo()).isEqualTo(1);
        assertThat(message.getCreatedAt()).isNotNull();
        assertRecentShanghai(message.getCreatedAt());
    }

    @Test
    void createPendingTaskShouldStampShanghaiLocalCreatedAt() {
        AtomicReference<QaRetrievalLogs> saved = new AtomicReference<>();
        QaRetrievalLogsServiceImpl service = new QaRetrievalLogsServiceImpl() {
            @Override
            public boolean save(QaRetrievalLogs entity) {
                saved.set(entity);
                return true;
            }

            @Override
            public QaRetrievalLogs getOne(Wrapper<QaRetrievalLogs> queryWrapper, boolean throwEx) {
                return null;
            }
        };

        QaRetrievalLogs task = service.createPendingTask(
                5L,
                "os",
                2L,
                11L,
                "basic",
                "请概括操作系统的目标和作用"
        );

        assertThat(saved.get()).isSameAs(task);
        assertThat(task.getTaskSeq()).isEqualTo(1);
        assertThat(task.getCreatedAt()).isNotNull();
        assertRecentShanghai(task.getCreatedAt());
    }

    private void assertRecentShanghai(LocalDateTime actual) {
        Duration delta = Duration.between(actual, LocalDateTime.now(SHANGHAI_ZONE)).abs();
        assertThat(delta).isLessThan(Duration.ofMinutes(1));
    }
}
