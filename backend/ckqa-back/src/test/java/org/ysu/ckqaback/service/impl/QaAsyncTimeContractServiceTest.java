package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.context.QaRetrievalLogContext;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
    void copyMessagesToSessionShouldKeepSequenceAndWriteProvenance() {
        List<QaMessages> saved = new ArrayList<>();
        QaMessages first = message(101L, 5L, "user", 1, "什么是死锁？");
        first.setTokenCount(6);
        QaMessages second = message(102L, 5L, "assistant", 2, "死锁是多个进程互相等待资源。");
        QaMessages third = message(103L, 5L, "user", 3, "那银行家算法呢？");
        QaMessagesServiceImpl service = new QaMessagesServiceImpl() {
            @Override
            public List<QaMessages> listBySessionId(Long sessionId) {
                assertThat(sessionId).isEqualTo(5L);
                return List.of(first, second, third);
            }

            @Override
            public boolean save(QaMessages entity) {
                saved.add(entity);
                return true;
            }
        };

        int copied = service.copyMessagesToSession(5L, 9L, 2);

        assertThat(copied).isEqualTo(2);
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getSessionId()).isEqualTo(9L);
        assertThat(saved.get(0).getSequenceNo()).isEqualTo(1);
        assertThat(saved.get(0).getContent()).isEqualTo("什么是死锁？");
        assertThat(saved.get(0).getContentText()).isEqualTo(first.getContentText());
        assertThat(saved.get(0).getTokenCount()).isEqualTo(6);
        assertThat(saved.get(0).getCopiedFromMessageId()).isEqualTo(101L);
        assertThat(saved.get(0).getCreatedAt()).isNotNull();
        assertThat(saved.get(1).getCopiedFromMessageId()).isEqualTo(102L);
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

    private QaMessages message(Long id, Long sessionId, String role, int sequenceNo, String content) {
        QaMessages message = new QaMessages();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setSequenceNo(sequenceNo);
        message.setContent(content);
        message.setContentText(content);
        message.setCreatedAt(LocalDateTime.of(2026, 5, 17, 10, sequenceNo));
        return message;
    }

    @Test
    void createPendingTaskShouldPersistModeAndTopicDiagnostics() {
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
                "drift",
                "关于上一轮主题「死锁」：它怎么检测？",
                new QaRetrievalLogContext(
                        "它怎么检测？",
                        "关于上一轮主题「死锁」：它怎么检测？",
                        "关于上一轮主题「死锁」：它怎么检测？",
                        "学生：什么是死锁？",
                        "recent",
                        "1-2",
                        30,
                        true,
                        "pronoun_follow_up",
                        "1-2",
                        "rule",
                        null,
                        0.92,
                        "phase3-v1",
                        null,
                        null,
                        null,
                        null,
                        false,
                        "none",
                        null,
                        0,
                        0,
                        null,
                        null,
                        null,
                        "smart",
                        "drift",
                        "死锁",
                        "history",
                        0.86,
                        "[{\"topic\":\"死锁\"}]"
                )
        );

        assertThat(saved.get()).isSameAs(task);
        assertThat(task.getRequestedMode()).isEqualTo("smart");
        assertThat(task.getResolvedMode()).isEqualTo("drift");
        assertThat(task.getResolvedTopic()).isEqualTo("死锁");
        assertThat(task.getTopicSource()).isEqualTo("history");
        assertThat(task.getTopicConfidence()).isEqualTo(0.86);
        assertThat(task.getTopicStackJson()).contains("死锁");
    }

    private void assertRecentShanghai(LocalDateTime actual) {
        Duration delta = Duration.between(actual, LocalDateTime.now(SHANGHAI_ZONE)).abs();
        assertThat(delta).isLessThan(Duration.ofMinutes(1));
    }
}
