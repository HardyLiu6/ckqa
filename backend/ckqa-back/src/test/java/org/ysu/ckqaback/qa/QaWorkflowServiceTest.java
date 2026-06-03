package org.ysu.ckqaback.qa;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.cache.StudentCacheKeyFactory;
import org.ysu.ckqaback.cache.StudentRedisCacheService;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.entity.IndexArtifacts;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessionSummaries;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.graphrag.GraphRagConversationMessage;
import org.ysu.ckqaback.integration.graphrag.GraphRagHybridReadinessResult;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskClient;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.QaClientRoutingSnapshot;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaHybridWarmupRequest;
import org.ysu.ckqaback.qa.dto.QaHybridWarmupResponse;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaSourceResponse;
import org.ysu.ckqaback.qa.dto.UpdateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;
import org.ysu.ckqaback.qa.context.BudgetSizeEstimate;
import org.ysu.ckqaback.qa.context.QaRetrievalLogContext;
import org.ysu.ckqaback.qa.context.QaTopicEntityBindingCandidate;
import org.ysu.ckqaback.qa.context.QaTopicEntityBindingResult;
import org.ysu.ckqaback.qa.context.QaTopicEntityBindingService;
import org.ysu.ckqaback.qa.context.TokenBudgetEstimator;
import org.ysu.ckqaback.qa.memory.QaMemoryContextResult;
import org.ysu.ckqaback.qa.memory.QaMemoryContextService;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.IndexArtifactsService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalHitsService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionSummariesService;
import org.ysu.ckqaback.service.QaSessionsService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class QaWorkflowServiceTest {

    @Test
    void shouldAllowSessionOwnerAccess() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                mock(KnowledgeBasesService.class),
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setUserId(7L);
        given(qaSessionsService.getRequiredById(5L)).willReturn(session);

        workflowService.ensureSessionOwner(5L, 7L);

        then(qaSessionsService).should().getRequiredById(5L);
    }

    @Test
    void shouldRejectSessionAccessForNonOwner() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                mock(KnowledgeBasesService.class),
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setUserId(9L);
        given(qaSessionsService.getRequiredById(5L)).willReturn(session);

        assertThatThrownBy(() -> workflowService.ensureSessionOwner(5L, 7L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ApiResultCode.AUTH_FORBIDDEN.getCode());
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                })
                .hasMessageContaining("只能访问自己的问答会话");
    }

    @Test
    void shouldRenameAndArchiveOwnedFormalSession() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                mock(KnowledgeBasesService.class),
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setUserId(7L);
        session.setSessionType("formal");
        session.setStatus("active");
        session.setTitle("旧标题");
        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(qaSessionsService.updateSession(5L, "死锁复习", "archived")).willAnswer(invocation -> {
            session.setTitle(invocation.getArgument(1));
            session.setStatus(invocation.getArgument(2));
            return session;
        });

        UpdateQaSessionRequest request = new UpdateQaSessionRequest();
        request.setTitle("死锁复习");
        request.setStatus("archived");

        var response = workflowService.updateSession(5L, request, authenticatedStudent());

        assertThat(response.getTitle()).isEqualTo("死锁复习");
        assertThat(response.getStatus()).isEqualTo("archived");
        then(qaSessionsService).should().updateSession(5L, "死锁复习", "archived");
    }

    @Test
    void shouldRejectRestoreWhenCoursePermissionIsLost() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                mock(KnowledgeBasesService.class),
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        workflowService.setCourseAccessService(courseAccessService);
        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setUserId(7L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setStatus("archived");
        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        doThrow(new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "无课程访问权限"))
                .when(courseAccessService).assertCourseReadable("os", "student.zhouzh");

        UpdateQaSessionRequest request = new UpdateQaSessionRequest();
        request.setStatus("active");

        assertThatThrownBy(() -> workflowService.updateSession(5L, request, authenticatedStudent()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getMessage()).contains("无课程访问权限");
                });

        then(qaSessionsService).should(never()).updateSession(anyLong(), any(), any());
    }

    @Test
    void shouldCreateFormalSessionWithLockedActiveIndexRunId() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        CreateQaSessionRequest request = new CreateQaSessionRequest();
        request.setUserId(7L);
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        request.setTitle("操作系统问答");

        QaSessions saved = new QaSessions();
        saved.setId(5L);
        saved.setSessionCode("qa-0001");
        saved.setUserId(7L);
        saved.setCourseId("os");
        saved.setKnowledgeBaseId(3L);
        saved.setSessionType("formal");
        saved.setTitle("操作系统问答");
        saved.setStatus("active");
        saved.setIndexRunId(17L);
        saved.setIndexLockedAt(LocalDateTime.of(2026, 5, 17, 10, 0));

        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaSessionsService.createSession(eq(request), eq(17L), any())).willReturn(saved);

        var response = workflowService.createSession(request);

        assertThat(response.getIndexRunId()).isEqualTo(17L);
        assertThat(response.getIndexLockedAt()).isEqualTo(LocalDateTime.of(2026, 5, 17, 10, 0));
    }

    @Test
    void shouldCreateFormalSessionOnlyWhenKnowledgeBaseBelongsToReadableCourse() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                knowledgeBasesService,
                usersService,
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        workflowService.setCourseAccessService(courseAccessService);

        CreateQaSessionRequest request = new CreateQaSessionRequest();
        request.setUserId(7L);
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        request.setTitle("操作系统问答");

        QaSessions saved = new QaSessions();
        saved.setId(5L);
        saved.setUserId(7L);
        saved.setCourseId("os");
        saved.setKnowledgeBaseId(3L);
        saved.setSessionType("formal");
        saved.setStatus("active");
        saved.setIndexRunId(17L);

        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaSessionsService.createSession(eq(request), eq(17L), any())).willReturn(saved);

        workflowService.createSession(request, authenticatedStudent());

        then(courseAccessService).should().assertCourseReadable("os", "student.zhouzh");
    }

    @Test
    void shouldWarmupHybridWithCourseScopeAndReadyOutputArtifact() {
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);
        IndexArtifactsService indexArtifactsService = mock(IndexArtifactsService.class);
        GraphRagTaskClient graphRagTaskClient = mock(GraphRagTaskClient.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                mock(QaSessionsService.class),
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                knowledgeBasesService,
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        workflowService.setCourseAccessService(courseAccessService);
        workflowService.setIndexArtifactsService(indexArtifactsService);
        workflowService.setGraphRagTaskClient(graphRagTaskClient);

        IndexArtifacts outputArtifact = new IndexArtifacts();
        outputArtifact.setIndexRunId(17L);
        outputArtifact.setArtifactType("output_dir");
        outputArtifact.setArtifactStatus("ready");
        outputArtifact.setStorageUri("user_2/kb_3/build_9/index/output");

        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(indexArtifactsService.listByIndexRunId(17L)).willReturn(List.of(outputArtifact));
        given(graphRagTaskClient.warmupHybridV0("user_2/kb_3/build_9/index/output"))
                .willReturn(new GraphRagHybridReadinessResult(
                        true,
                        "ready",
                        null,
                        "user_2/kb_3/build_9/index/output",
                        true,
                        true,
                        List.of()
                ));

        QaHybridWarmupRequest request = new QaHybridWarmupRequest();
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        QaHybridWarmupResponse response = workflowService.warmupHybrid(request, authenticatedStudent());

        assertThat(response.isReady()).isTrue();
        assertThat(response.getDataDirUri()).isEqualTo("user_2/kb_3/build_9/index/output");
        then(courseAccessService).should().assertCourseReadable("os", "student.zhouzh");
    }

    @Test
    void shouldReturnCachedHybridWarmupWithoutCallingGraphRagClient() {
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);
        IndexArtifactsService indexArtifactsService = mock(IndexArtifactsService.class);
        GraphRagTaskClient graphRagTaskClient = mock(GraphRagTaskClient.class);
        StudentRedisCacheService cacheService = mock(StudentRedisCacheService.class);
        StudentCacheKeyFactory keyFactory = mock(StudentCacheKeyFactory.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                mock(QaSessionsService.class),
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                knowledgeBasesService,
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        workflowService.setCourseAccessService(courseAccessService);
        workflowService.setIndexArtifactsService(indexArtifactsService);
        workflowService.setGraphRagTaskClient(graphRagTaskClient);
        workflowService.setStudentRedisCacheService(cacheService);
        workflowService.setStudentCacheKeyFactory(keyFactory);

        IndexArtifacts outputArtifact = new IndexArtifacts();
        outputArtifact.setIndexRunId(17L);
        outputArtifact.setArtifactType("output_dir");
        outputArtifact.setArtifactStatus("ready");
        outputArtifact.setStorageUri("user_2/kb_3/build_9/index/output");

        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(indexArtifactsService.listByIndexRunId(17L)).willReturn(List.of(outputArtifact));
        given(keyFactory.hybridReadinessKey(3L, 17L, "user_2/kb_3/build_9/index/output"))
                .willReturn("hybrid-key");
        given(cacheService.get("hybrid-key", QaHybridWarmupResponse.class)).willReturn(QaHybridWarmupResponse.of(
                true,
                "ready",
                "混合检索已就绪",
                "user_2/kb_3/build_9/index/output",
                false,
                true,
                List.of()
        ));

        QaHybridWarmupRequest request = new QaHybridWarmupRequest();
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        QaHybridWarmupResponse response = workflowService.warmupHybrid(request, authenticatedStudent());

        assertThat(response.isReady()).isTrue();
        assertThat(response.isCached()).isTrue();
        then(graphRagTaskClient).should(never()).warmupHybridV0(any());
    }

    @Test
    void shouldRejectFormalSessionWhenKnowledgeBaseCourseDoesNotMatchRequestCourse() {
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                mock(QaSessionsService.class),
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                knowledgeBasesService,
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );

        KnowledgeBases knowledgeBase = buildKnowledgeBase();
        knowledgeBase.setCourseId("os");
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(knowledgeBase);

        CreateQaSessionRequest request = new CreateQaSessionRequest();
        request.setUserId(7L);
        request.setCourseId("database");
        request.setKnowledgeBaseId(3L);

        assertThatThrownBy(() -> workflowService.createSession(request, authenticatedStudent()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识库不属于当前课程");
    }

    @Test
    void shouldRejectFormalSessionWhenCourseIsNotReadableByCurrentUser() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        CourseAccessService courseAccessService = mock(CourseAccessService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                knowledgeBasesService,
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        workflowService.setCourseAccessService(courseAccessService);

        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        doThrow(new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.FORBIDDEN, "无课程访问权限"))
                .when(courseAccessService).assertCourseReadable("os", "student.zhouzh");

        CreateQaSessionRequest request = new CreateQaSessionRequest();
        request.setUserId(7L);
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);

        assertThatThrownBy(() -> workflowService.createSession(request, authenticatedStudent()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getMessage()).contains("无课程访问权限");
                });

        then(qaSessionsService).should(never()).createSession(any(), any(), any());
    }

    @Test
    void shouldRejectPublicCreateSessionWhenRequestingSmokeSession() {
        QaWorkflowService workflowService = new QaWorkflowService(
                mock(QaSessionsService.class),
                mock(QaMessagesService.class),
                mock(QaRetrievalLogsService.class),
                mock(KnowledgeBasesService.class),
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );

        CreateQaSessionRequest request = new CreateQaSessionRequest();
        request.setUserId(7L);
        request.setCourseId("os");
        request.setKnowledgeBaseId(3L);
        request.setSessionType("smoke");

        assertThatThrownBy(() -> workflowService.createSession(request, authenticatedStudent()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ApiResultCode.AUTH_FORBIDDEN.getCode());
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                })
                .hasMessageContaining("学生端不能创建 smoke 会话");
    }

    @Test
    void shouldSubmitTaskAndDispatchWorkerWithoutAssistantMessage() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(23L);

        QaMessages userMessage = new QaMessages();
        userMessage.setId(11L);
        userMessage.setSessionId(5L);
        userMessage.setRole("user");
        userMessage.setSequenceNo(1);
        userMessage.setContent("请概括这套图谱的主题");

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(qaMessagesService.appendUserMessage(5L, "请概括这套图谱的主题")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(11L),
                eq("basic"),
                eq("请概括这套图谱的主题"),
                argThat(context -> "none".equals(context.contextStrategy())
                        && "请概括这套图谱的主题".equals(context.originalQueryText())
                        && "请概括这套图谱的主题".equals(context.retrievalQueryText()))
        )).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "请概括这套图谱的主题"));

        assertThat(response.getTaskId()).isEqualTo(9001L);
        assertThat(response.getMode()).isEqualTo("basic");
        assertThat(response.getTaskStatus()).isEqualTo("pending");
        assertThat(response.getProgressStage()).isEqualTo("queued");
        assertThat(response.getRecommendedPollingIntervalSeconds()).isEqualTo(10);
        assertThat(response.getStaleTimeoutSeconds()).isEqualTo(300);
        assertThat(response.getTimeoutMessage()).contains("basic");
        assertThat(response.getContextApplied()).isFalse();
        assertThat(response.getContextStrategy()).isEqualTo("none");
        assertThat(response.getContextSizeEstimate().getChars()).isZero();
        then(qaTaskWorker).should().dispatch(5L, 9001L);
        then(qaMessagesService).should(never()).appendAssistantMessage(anyLong(), eq("请概括这套图谱的主题"));
    }

    @Test
    void shouldSubmitLocalTaskWithMemoryHistoryMetadata() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);
        QaMemoryContextService memoryContextService = mock(QaMemoryContextService.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );
        workflowService.setQaMemoryContextService(memoryContextService);

        QaSessions session = formalSession();
        QaMessages previousUser = message(101L, 5L, "user", 1, "什么是时间片轮转？");
        QaMessages previousAssistant = message(102L, 5L, "assistant", 2, "时间片轮转是一种抢占式调度算法。");
        QaMessages userMessage = message(103L, 5L, "user", 3, "它为什么影响响应时间？");
        QaRetrievalLogs task = pendingTask(9009L);
        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(previousUser, previousAssistant));
        given(memoryContextService.buildContext(
                "local",
                "auto",
                session,
                List.of(previousUser, previousAssistant),
                "它为什么影响响应时间？",
                "时间片轮转"
        ))
                .willReturn(new QaMemoryContextResult(
                        true,
                        "local_history_preference_only",
                        "userId=7;courseId=os;knowledgeBaseId=3;indexRunId=23",
                        2,
                        66,
                        List.of(
                                new GraphRagConversationMessage("assistant", "学习记忆：偏好步骤化解释。"),
                                new GraphRagConversationMessage("user", "什么是时间片轮转？")
                        ),
                        null,
                        "memory-governance-v1",
                        1,
                        1,
                        "preference_enabled:auto",
                        "[{\"memoryId\":101,\"memoryType\":\"explanation_preference\",\"textHash\":\"abc12345\",\"textChars\":10}]"
                ));
        given(qaMessagesService.appendUserMessage(5L, "它为什么影响响应时间？")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(103L),
                eq("local"),
                eq("关于上一轮主题「时间片轮转」：它为什么影响响应时间？"),
                argThat(context -> context.memoryApplied()
                        && "local_history_preference_only".equals(context.memoryStrategy())
                        && "local_history".equals(context.queryEngineStrategy())
                        && context.memoryHistoryJson().contains("学习记忆")
                        && context.memorySourceCount() == 2
                        && "memory-governance-v1".equals(context.memoryGovernanceVersion())
                        && context.memoryLongTermCount() == 1
                        && context.memoryRecentHistoryCount() == 1
                        && "preference_enabled:auto".equals(context.memoryInjectionReason())
                        && context.memorySourcesJson().contains("\"textHash\":\"abc12345\"")
                        && !context.memorySourcesJson().contains("偏好步骤化解释"))
        )).willReturn(task);

        CreateQaMessageRequest request = new CreateQaMessageRequest("local", "它为什么影响响应时间？");
        request.setMemoryPolicy("auto");
        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, request);

        assertThat(response.getMemoryApplied()).isTrue();
        assertThat(response.getMemoryStrategy()).isEqualTo("local_history_preference_only");
        assertThat(response.getMemoryScope()).contains("indexRunId=23");
        assertThat(response.getMemorySourceCount()).isEqualTo(2);
        assertThat(response.getMemorySizeEstimate()).isEqualTo(66);
    }

    @Test
    void shouldWriteTokenBudgetEstimatesIntoPendingTaskContextAndResponse() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaMemoryContextService memoryContextService = mock(QaMemoryContextService.class);
        TokenBudgetEstimator tokenBudgetEstimator = mock(TokenBudgetEstimator.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        workflowService.setQaMemoryContextService(memoryContextService);
        workflowService.setTokenBudgetEstimator(tokenBudgetEstimator);

        QaSessions session = formalSession();
        QaMessages previousUser = message(101L, 5L, "user", 1, "什么是时间片轮转？");
        QaMessages previousAssistant = message(102L, 5L, "assistant", 2, "时间片轮转是一种抢占式调度算法。");
        QaMessages userMessage = message(103L, 5L, "user", 3, "它为什么影响响应时间？");
        QaRetrievalLogs task = pendingTask(9016L);
        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(previousUser, previousAssistant));
        given(memoryContextService.buildContext(
                "local",
                "auto",
                session,
                List.of(previousUser, previousAssistant),
                "它为什么影响响应时间？",
                "时间片轮转"
        )).willReturn(new QaMemoryContextResult(
                true,
                "local_history_preference_only",
                "userId=7;courseId=os;knowledgeBaseId=3;indexRunId=23",
                2,
                66,
                List.of(
                        new GraphRagConversationMessage("assistant", "学习记忆：偏好步骤化解释。"),
                        new GraphRagConversationMessage("user", "什么是时间片轮转？")
                ),
                null
        ));
        given(tokenBudgetEstimator.estimate(any(), any()))
                .willAnswer(invocation -> new BudgetSizeEstimate(
                        invocation.getArgument(1, Integer.class),
                        17,
                        "test:o200k_base",
                        null
                ));
        given(qaMessagesService.appendUserMessage(5L, "它为什么影响响应时间？")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(103L),
                eq("local"),
                eq("关于上一轮主题「时间片轮转」：它为什么影响响应时间？"),
                argThat(context -> context.contextTokenCount() == 17
                        && "test:o200k_base".equals(context.contextTokenizer())
                        && context.contextBudgetFallbackReason() == null
                        && context.memoryTokenCount() == 17
                        && "test:o200k_base".equals(context.memoryTokenizer())
                        && context.memoryBudgetFallbackReason() == null)
        )).willReturn(task);

        CreateQaMessageRequest request = new CreateQaMessageRequest("local", "它为什么影响响应时间？");
        request.setMemoryPolicy("auto");
        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, request);

        assertThat(response.getContextSizeEstimate().getTokens()).isEqualTo(17);
        assertThat(response.getContextSizeEstimate().getTokenizer()).isEqualTo("test:o200k_base");
        assertThat(response.getContextSizeEstimate().getFallbackReason()).isNull();
    }

    @Test
    void shouldResolveSmartModeToRecommendedDriftForPendingTaskAndResponse() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                mock(UsersService.class),
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = formalSession();
        QaMessages previousUser = message(101L, 5L, "user", 1, "什么是死锁？");
        QaMessages previousAssistant = message(102L, 5L, "assistant", 2, "死锁是多个进程互相等待资源的状态。");
        QaMessages userMessage = message(103L, 5L, "user", 3, "它和资源分配图有什么关系？");
        QaRetrievalLogs task = pendingTask(9012L);
        QaClientRoutingSnapshot snapshot = new QaClientRoutingSnapshot();
        snapshot.setSelectedMode("smart");
        snapshot.setRecommendedMode("drift");
        CreateQaMessageRequest request = new CreateQaMessageRequest("smart", "它和资源分配图有什么关系？");
        request.setClientRoutingSnapshot(snapshot);

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(previousUser, previousAssistant));
        given(qaMessagesService.appendUserMessage(5L, "它和资源分配图有什么关系？")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(103L),
                eq("drift"),
                eq("关于上一轮主题「死锁」：它和资源分配图有什么关系？"),
                argThat(context -> "recent".equals(context.contextStrategy())
                        && "smart".equals(context.requestedMode())
                        && "drift".equals(context.resolvedMode())
                        && "死锁".equals(context.resolvedTopic())
                        && "history".equals(context.topicSource())
                        && context.topicConfidence() >= 0.8
                        && context.topicStackJson().contains("死锁")
                        && context.rewriteApplied()
                        && context.queryEngineStrategy() == null
                        && context.routingSnapshotJson().contains("\"recommendedMode\":\"drift\""))
        )).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, request);

        assertThat(response.getMode()).isEqualTo("drift");
        assertThat(response.getRequestedMode()).isEqualTo("smart");
        assertThat(response.getResolvedMode()).isEqualTo("drift");
        assertThat(response.getUserMessage().getMode()).isEqualTo("drift");
        assertThat(response.getRecommendedPollingIntervalSeconds()).isEqualTo(15);
        assertThat(response.getContextStrategy()).isEqualTo("recent");
        then(qaTaskWorker).should().dispatch(5L, 9012L);
    }

    @Test
    void shouldWriteTopicEntityBindingDiagnosticsIntoLogContext() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaTopicEntityBindingService bindingService = mock(QaTopicEntityBindingService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        workflowService.setQaTopicEntityBindingService(bindingService);

        QaSessions session = formalSession();
        QaMessages previousUser = message(101L, 5L, "user", 1, "什么是死锁？");
        QaMessages previousAssistant = message(102L, 5L, "assistant", 2, "死锁是多个进程互相等待资源的状态。");
        QaMessages userMessage = message(103L, 5L, "user", 3, "它和资源分配图有什么关系？");
        QaRetrievalLogs task = pendingTask(9015L);
        QaTopicEntityBindingCandidate candidate = new QaTopicEntityBindingCandidate(
                "entity-deadlock",
                "死锁",
                "concept",
                "E-42",
                1.0D,
                "exact_name",
                "active_neo4j"
        );

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(previousUser, previousAssistant));
        given(bindingService.bind(any(), eq(3L), eq(23L)))
                .willReturn(QaTopicEntityBindingResult.success(List.of(candidate), 12L));
        given(qaMessagesService.appendUserMessage(5L, "它和资源分配图有什么关系？")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(103L),
                eq("basic"),
                eq("关于上一轮主题「死锁」：它和资源分配图有什么关系？"),
                any()
        )).willReturn(task);

        workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "它和资源分配图有什么关系？"));

        ArgumentCaptor<QaRetrievalLogContext> contextCaptor = ArgumentCaptor.forClass(QaRetrievalLogContext.class);
        then(qaRetrievalLogsService).should().createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(103L),
                eq("basic"),
                eq("关于上一轮主题「死锁」：它和资源分配图有什么关系？"),
                contextCaptor.capture()
        );
        QaRetrievalLogContext context = contextCaptor.getValue();
        assertThat(context.topicEntityBindingApplied()).isTrue();
        assertThat(context.topicEntityBindingStatus()).isEqualTo("success");
        assertThat(context.topicEntityBindingStrategy()).isEqualTo("active_neo4j_topic_match");
        assertThat(context.topicEntityCandidateCount()).isEqualTo(1);
        assertThat(context.topicEntityTopScore()).isEqualTo(1.0D);
        assertThat(context.topicEntitySelectedId()).isEqualTo("entity-deadlock");
        assertThat(context.topicEntitySelectedName()).isEqualTo("死锁");
        assertThat(context.topicEntitySelectedType()).isEqualTo("concept");
        assertThat(context.topicEntityCandidatesJson()).contains("\"id\":\"entity-deadlock\"");
        assertThat(context.topicEntityCandidatesJson()).contains("\"source\":\"active_neo4j\"");
        assertThat(context.topicEntityCandidatesJson()).doesNotContain("description", "snippet", "memoryText", "full_content");
        assertThat(context.topicEntityLookupDurationMs()).isEqualTo(12L);
        then(bindingService).should().bind(any(), eq(3L), eq(23L));
    }

    @Test
    void shouldResolveSmartModeToRecommendedLocalForMemoryAndPendingTask() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaMemoryContextService memoryContextService = mock(QaMemoryContextService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        workflowService.setQaMemoryContextService(memoryContextService);

        QaSessions session = formalSession();
        QaMessages previousUser = message(101L, 5L, "user", 1, "什么是时间片轮转？");
        QaMessages previousAssistant = message(102L, 5L, "assistant", 2, "时间片轮转是一种抢占式调度算法。");
        QaMessages userMessage = message(103L, 5L, "user", 3, "它为什么影响响应时间？");
        QaRetrievalLogs task = pendingTask(9013L);
        QaClientRoutingSnapshot snapshot = new QaClientRoutingSnapshot();
        snapshot.setSelectedMode("smart");
        snapshot.setRecommendedMode("local");
        CreateQaMessageRequest request = new CreateQaMessageRequest("smart", "它为什么影响响应时间？");
        request.setClientRoutingSnapshot(snapshot);
        request.setMemoryPolicy("auto");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(previousUser, previousAssistant));
        given(memoryContextService.buildContext(
                "local",
                "auto",
                session,
                List.of(previousUser, previousAssistant),
                "它为什么影响响应时间？",
                "时间片轮转"
        )).willReturn(new QaMemoryContextResult(
                true,
                "local_history_preference_only",
                "userId=7;courseId=os;knowledgeBaseId=3;indexRunId=23",
                1,
                44,
                List.of(new GraphRagConversationMessage("assistant", "学习记忆：偏好步骤化解释。")),
                null
        ));
        given(qaMessagesService.appendUserMessage(5L, "它为什么影响响应时间？")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(103L),
                eq("local"),
                eq("关于上一轮主题「时间片轮转」：它为什么影响响应时间？"),
                argThat(context -> context.memoryApplied()
                        && "local_history_preference_only".equals(context.memoryStrategy())
                        && "local_history".equals(context.queryEngineStrategy()))
        )).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, request);

        assertThat(response.getMode()).isEqualTo("local");
        assertThat(response.getRequestedMode()).isEqualTo("smart");
        assertThat(response.getResolvedMode()).isEqualTo("local");
        assertThat(response.getMemoryApplied()).isTrue();
        assertThat(response.getMemoryStrategy()).isEqualTo("local_history_preference_only");
    }

    @Test
    void shouldFallbackSmartModeToBasicWhenRecommendedModeIsInvalid() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );

        QaSessions session = formalSession();
        QaMessages userMessage = message(103L, 5L, "user", 1, "请概括这套图谱的主题");
        QaRetrievalLogs task = pendingTask(9014L);
        QaClientRoutingSnapshot snapshot = new QaClientRoutingSnapshot();
        snapshot.setSelectedMode("smart");
        snapshot.setRecommendedMode("unsupported");
        CreateQaMessageRequest request = new CreateQaMessageRequest("smart", "请概括这套图谱的主题");
        request.setClientRoutingSnapshot(snapshot);

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(qaMessagesService.appendUserMessage(5L, "请概括这套图谱的主题")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(103L),
                eq("basic"),
                eq("请概括这套图谱的主题"),
                argThat(context -> "none".equals(context.contextStrategy())
                        && context.queryEngineStrategy() == null)
        )).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, request);

        assertThat(response.getMode()).isEqualTo("basic");
        assertThat(response.getRequestedMode()).isEqualTo("smart");
        assertThat(response.getResolvedMode()).isEqualTo("basic");
        assertThat(response.getUserMessage().getMode()).isEqualTo("basic");
    }

    @Test
    void shouldNotApplyMemoryWhenPolicyIsOff() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaMemoryContextService memoryContextService = mock(QaMemoryContextService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        workflowService.setQaMemoryContextService(memoryContextService);

        QaSessions session = formalSession();
        QaMessages userMessage = message(103L, 5L, "user", 3, "问题");
        QaRetrievalLogs task = pendingTask(9010L);
        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(memoryContextService.buildContext("local", "off", session, List.of(), "问题", "")).willReturn(QaMemoryContextResult.notApplied("policy_off"));
        given(qaMessagesService.appendUserMessage(5L, "问题")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L), eq("os"), eq(23L), eq(103L), eq("local"), eq("问题"),
                argThat(context -> !context.memoryApplied() && "policy_off".equals(context.historyFallbackReason()))
        )).willReturn(task);

        CreateQaMessageRequest request = new CreateQaMessageRequest("local", "问题");
        request.setMemoryPolicy("off");
        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, request);

        assertThat(response.getMemoryApplied()).isFalse();
        assertThat(response.getMemoryStrategy()).isEqualTo("none");
    }

    @Test
    void shouldNotApplyMemoryForNonLocalMode() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        QaMemoryContextService memoryContextService = mock(QaMemoryContextService.class);
        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                mock(UsersService.class),
                mock(QaTaskWorker.class),
                buildTaskPolicyProperties()
        );
        workflowService.setQaMemoryContextService(memoryContextService);

        QaSessions session = formalSession();
        QaMessages userMessage = message(103L, 5L, "user", 3, "问题");
        QaRetrievalLogs task = pendingTask(9011L);
        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(memoryContextService.buildContext("basic", "auto", session, List.of(), "问题", "")).willReturn(QaMemoryContextResult.notApplied("mode_not_local"));
        given(qaMessagesService.appendUserMessage(5L, "问题")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L), eq("os"), eq(23L), eq(103L), eq("basic"), eq("问题"),
                argThat(context -> !context.memoryApplied() && context.queryEngineStrategy() == null)
        )).willReturn(task);

        CreateQaMessageRequest request = new CreateQaMessageRequest("basic", "问题");
        request.setMemoryPolicy("auto");
        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, request);

        assertThat(response.getMemoryApplied()).isFalse();
    }

    @Test
    void shouldPersistClientRoutingSnapshotWhenCreatingPendingTask() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(23L);

        QaMessages userMessage = message(11L, 5L, "user", 1, "请帮我复习一下。");
        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9008L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        QaClientRoutingSnapshot snapshot = new QaClientRoutingSnapshot();
        snapshot.setSelectedMode("smart");
        snapshot.setRecommendedMode("basic");
        snapshot.setFallbackMode("local");
        snapshot.setConfidence(0.59D);
        snapshot.setConfidenceBand("low_confidence");
        snapshot.setReviewPriority("low_confidence");
        snapshot.setManualSwitchSuggested(true);
        snapshot.setReasons(List.of("default_basic"));
        CreateQaMessageRequest request = new CreateQaMessageRequest("basic", "请帮我复习一下。");
        request.setClientRoutingSnapshot(snapshot);

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(qaMessagesService.appendUserMessage(5L, "请帮我复习一下。")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(11L),
                eq("basic"),
                eq("请帮我复习一下。"),
                argThat(context -> Double.valueOf(0.59D).equals(context.routingConfidence())
                        && "low_confidence".equals(context.routingConfidenceBand())
                        && "low_confidence".equals(context.routingReviewPriority())
                        && context.routingSnapshotJson().contains("\"selectedMode\":\"smart\""))
        )).willReturn(task);

        workflowService.sendMessage(5L, request);

        then(qaTaskWorker).should().dispatch(5L, 9008L);
    }

    @Test
    void shouldSubmitHybridTaskWithRecentContextAndRewrite() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(23L);

        QaMessages previousUser = message(101L, 5L, "user", 1, "什么是死锁？");
        QaMessages previousAssistant = message(102L, 5L, "assistant", 2, "死锁是多个进程互相等待资源的状态。");
        QaMessages userMessage = message(103L, 5L, "user", 3, "它和资源分配图有什么关系？");

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9005L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(previousUser, previousAssistant));
        given(qaMessagesService.appendUserMessage(5L, "它和资源分配图有什么关系？")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(103L),
                eq("hybrid_v0"),
                eq("关于上一轮主题「死锁」：它和资源分配图有什么关系？"),
                argThat(context -> "recent".equals(context.contextStrategy()) && context.rewriteApplied())
        )).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("hybrid_v0", "它和资源分配图有什么关系？"));

        assertThat(response.getMode()).isEqualTo("hybrid_v0");
        assertThat(response.getRecommendedPollingIntervalSeconds()).isEqualTo(30);
        assertThat(response.getStaleTimeoutSeconds()).isEqualTo(1800);
        assertThat(response.getContextStrategy()).isEqualTo("recent");
    }

    @Test
    void shouldRewritePronounFollowUpBeforeCreatingPendingTask() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(23L);

        QaMessages previousUser = message(101L, 5L, "user", 1, "什么是死锁？");
        QaMessages previousAssistant = message(102L, 5L, "assistant", 2, "死锁是多个进程互相等待资源的状态。");
        QaMessages userMessage = message(103L, 5L, "user", 3, "它和资源分配图有什么关系？");

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9002L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(previousUser, previousAssistant));
        given(qaMessagesService.appendUserMessage(5L, "它和资源分配图有什么关系？")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(103L),
                eq("basic"),
                eq("关于上一轮主题「死锁」：它和资源分配图有什么关系？"),
                argThat(context -> "recent".equals(context.contextStrategy())
                        && context.rewriteApplied()
                        && "1-2".equals(context.rewriteSourceMessageRange())
                        && context.contextCharCount() > 0)
        )).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "它和资源分配图有什么关系？"));

        assertThat(response.getTaskId()).isEqualTo(9002L);
        assertThat(response.getContextApplied()).isTrue();
        assertThat(response.getContextStrategy()).isEqualTo("recent");
        assertThat(response.getContextSizeEstimate().getChars()).isGreaterThan(0);
    }

    @Test
    void shouldUseLatestSummaryWhenCreatingContextForLongSession() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        QaSessionSummariesService qaSessionSummariesService = mock(QaSessionSummariesService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );
        workflowService.setQaSessionSummariesService(qaSessionSummariesService);

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(23L);

        QaSessionSummaries summary = new QaSessionSummaries();
        summary.setSessionId(5L);
        summary.setSummaryText("本会话已讨论死锁定义。");
        summary.setSummaryUntilSequenceNo(2);
        summary.setStatus("success");

        QaMessages summarizedUser = message(101L, 5L, "user", 1, "什么是死锁？");
        QaMessages summarizedAssistant = message(102L, 5L, "assistant", 2, "死锁是多个进程互相等待资源的状态。");
        QaMessages recentUser = message(103L, 5L, "user", 3, "那银行家算法呢？");
        QaMessages recentAssistant = message(104L, 5L, "assistant", 4, "银行家算法用于避免进入不安全状态。");
        QaMessages userMessage = message(105L, 5L, "user", 5, "它怎么判断安全？");

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9004L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(summarizedUser, summarizedAssistant, recentUser, recentAssistant));
        given(qaSessionSummariesService.findLatestSuccessfulBySessionId(5L)).willReturn(summary);
        given(qaMessagesService.appendUserMessage(5L, "它怎么判断安全？")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(23L),
                eq(105L),
                eq("basic"),
                eq("关于上一轮主题「银行家算法」：它怎么判断安全？"),
                argThat(context -> "summary_recent".equals(context.contextStrategy())
                        && context.contextSnapshotText().contains("会话摘要")
                        && context.contextSnapshotText().contains("最近对话")
                        && "银行家算法".equals(context.resolvedTopic())
                        && context.contextCharCount() > 0)
        )).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "它怎么判断安全？"));

        assertThat(response.getContextStrategy()).isEqualTo("summary_recent");
    }

    @Test
    void shouldBackfillLegacyFormalSessionIndexRunIdFromUniqueSuccessfulTask() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");

        QaMessages userMessage = message(11L, 5L, "user", 1, "继续提问");
        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9003L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaRetrievalLogsService.findDistinctSuccessfulIndexRunIdsBySession(5L)).willReturn(List.of(18L));
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(qaMessagesService.appendUserMessage(5L, "继续提问")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(
                eq(5L),
                eq("os"),
                eq(18L),
                eq(11L),
                eq("basic"),
                eq("继续提问"),
                any(QaRetrievalLogContext.class)
        )).willReturn(task);

        workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "继续提问"));

        then(qaSessionsService).should().lockIndexRun(eq(5L), eq(18L), any());
        ArgumentCaptor<QaRetrievalLogContext> contextCaptor = ArgumentCaptor.forClass(QaRetrievalLogContext.class);
        then(qaRetrievalLogsService).should().createPendingTask(
                eq(5L),
                eq("os"),
                eq(18L),
                eq(11L),
                eq("basic"),
                eq("继续提问"),
                contextCaptor.capture()
        );
        assertThat(contextCaptor.getValue().semanticStateVersion()).isEqualTo("session_semantic_state_v1");
        assertThat(contextCaptor.getValue().semanticStateJson()).contains("\"version\":\"session_semantic_state_v1\"");
    }

    @Test
    void shouldRejectLegacyFormalSessionWhenIndexRunIdCannotBeBackfilled() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaRetrievalLogsService.findDistinctSuccessfulIndexRunIdsBySession(5L)).willReturn(List.of(18L, 19L));

        assertThatThrownBy(() -> workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "继续提问")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("索引版本固化前");

        then(qaMessagesService).should(never()).appendUserMessage(anyLong(), any());
    }

    @Test
    void shouldDispatchWorkerAfterTransactionCommitWhenSynchronizationActive() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(17L);

        QaMessages userMessage = new QaMessages();
        userMessage.setId(11L);
        userMessage.setSessionId(5L);
        userMessage.setRole("user");
        userMessage.setSequenceNo(1);
        userMessage.setContent("请概括这套图谱的主题");

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(qaMessagesService.appendUserMessage(5L, "请概括这套图谱的主题")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(eq(5L), eq("os"), eq(17L), eq(11L), eq("basic"), eq("请概括这套图谱的主题"), any(QaRetrievalLogContext.class))).willReturn(task);

        TransactionSynchronizationManager.initSynchronization();
        try {
            workflowService.sendMessage(5L, new CreateQaMessageRequest("basic", "请概括这套图谱的主题"));

            then(qaTaskWorker).should(never()).dispatch(5L, 9001L);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());

            then(qaTaskWorker).should().dispatch(5L, 9001L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldReturnModeSpecificPollingHintsForDriftSubmission() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(17L);

        QaMessages userMessage = new QaMessages();
        userMessage.setId(11L);
        userMessage.setSessionId(5L);
        userMessage.setRole("user");
        userMessage.setSequenceNo(1);
        userMessage.setContent("请用 drift 模式回答");

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(knowledgeBasesService.getRequiredById(3L)).willReturn(buildKnowledgeBase());
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of());
        given(qaMessagesService.appendUserMessage(5L, "请用 drift 模式回答")).willReturn(userMessage);
        given(qaRetrievalLogsService.createPendingTask(eq(5L), eq("os"), eq(17L), eq(11L), eq("drift"), eq("请用 drift 模式回答"), any(QaRetrievalLogContext.class))).willReturn(task);

        QaTaskSubmissionResponse response = workflowService.sendMessage(5L, new CreateQaMessageRequest("drift", "请用 drift 模式回答"));

        assertThat(response.getMode()).isEqualTo("drift");
        assertThat(response.getRecommendedPollingIntervalSeconds()).isEqualTo(15);
        assertThat(response.getStaleTimeoutSeconds()).isEqualTo(1800);
        assertThat(response.getTimeoutMessage()).contains("drift");
    }

    @Test
    void shouldListMessagesWithTaskSummaryOnlyOnUserMessages() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalHitsService qaRetrievalHitsService = mock(QaRetrievalHitsService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );
        workflowService.setQaRetrievalHitsService(qaRetrievalHitsService);

        QaSessions session = new QaSessions();
        session.setId(5L);

        QaMessages userMessage = new QaMessages();
        userMessage.setId(101L);
        userMessage.setSessionId(5L);
        userMessage.setRole("user");
        userMessage.setSequenceNo(1);
        userMessage.setContent("请概括这套图谱的主题");
        userMessage.setCreatedAt(LocalDateTime.of(2026, 4, 22, 20, 0));

        QaMessages assistantMessage = new QaMessages();
        assistantMessage.setId(102L);
        assistantMessage.setSessionId(5L);
        assistantMessage.setRole("assistant");
        assistantMessage.setSequenceNo(2);
        assistantMessage.setContent("图谱主题集中在操作系统概念网络");
        assistantMessage.setCreatedAt(LocalDateTime.of(2026, 4, 22, 20, 1));

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setUserMessageId(101L);
        task.setAssistantMessageId(102L);
        task.setQueryMode("global");
        task.setTaskStatus("running");
        task.setProgressStage("running");
        task.setLatestLogs("""
                [
                  {
                    "type": "context_selected",
                    "mode": "global",
                    "summary": "已选取 3 份课程报告作为全局总结依据。",
                    "metrics": {"reportCount": 3},
                    "evidence": [{"kind": "report", "title": "操作系统第一章报告"}],
                    "eventSeq": 12
                  }
                ]
                """);
        task.setPartialResponseText("当前已经生成的部分回答");
        task.setStreamEventSeq(12L);

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(qaMessagesService.listBySessionId(5L)).willReturn(List.of(userMessage, assistantMessage));
        given(qaRetrievalLogsService.findLatestByUserMessageIds(List.of(101L))).willReturn(Map.of(101L, task));
        given(qaRetrievalHitsService.findSourcesByRetrievalLogIds(List.of(9001L))).willReturn(Map.of(
                9001L,
                List.of(QaSourceResponse.of(1, "doc-1", "chunk-1", "156", "操作系统教材", "第3章/死锁", 123, 124, "死锁来源片段"))
        ));

        List<QaMessageResponse> responses = workflowService.listMessages(5L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getTaskStatus()).isEqualTo("running");
        assertThat(responses.get(0).getProgressStage()).isEqualTo("running");
        assertThat(responses.get(0).getMode()).isEqualTo("global");
        assertThat(responses.get(0).getTaskId()).isEqualTo(9001L);
        assertThat(responses.get(0).getLatestLogs()).containsExactly("已选取 3 份课程报告作为全局总结依据。");
        assertThat(responses.get(0).getProgressEvents()).hasSize(1);
        assertThat(responses.get(0).getProgressEvents().get(0).getType()).isEqualTo("context_selected");
        assertThat(responses.get(0).getProgressEvents().get(0).getMetrics().get("reportCount")).isEqualTo(3);
        assertThat(responses.get(0).getProgressEvents().get(0).getEventSeq()).isEqualTo(12L);
        assertThat(responses.get(0).getPartialResponseText()).isEqualTo("当前已经生成的部分回答");
        assertThat(responses.get(0).getStreamEventSeq()).isEqualTo(12L);
        assertThat(responses.get(1).getTaskStatus()).isNull();
        assertThat(responses.get(1).getProgressStage()).isNull();
        assertThat(responses.get(1).getMode()).isEqualTo("global");
        assertThat(responses.get(1).getTaskId()).isEqualTo(9001L);
        assertThat(responses.get(1).getLatestLogs()).containsExactly("已选取 3 份课程报告作为全局总结依据。");
        assertThat(responses.get(1).getProgressEvents()).hasSize(1);
        assertThat(responses.get(1).getPartialResponseText()).isNull();
        assertThat(responses.get(1).getStreamEventSeq()).isEqualTo(12L);
        assertThat(responses.get(1).getSources()).hasSize(1);
        assertThat(responses.get(1).getSources().get(0).getSourceFile()).isEqualTo("操作系统教材");
    }

    @Test
    void shouldReturnModeSpecificPollingHintsForTaskDetail() {
        QaSessionsService qaSessionsService = mock(QaSessionsService.class);
        QaMessagesService qaMessagesService = mock(QaMessagesService.class);
        QaRetrievalLogsService qaRetrievalLogsService = mock(QaRetrievalLogsService.class);
        KnowledgeBasesService knowledgeBasesService = mock(KnowledgeBasesService.class);
        UsersService usersService = mock(UsersService.class);
        QaTaskWorker qaTaskWorker = mock(QaTaskWorker.class);

        QaWorkflowService workflowService = new QaWorkflowService(
                qaSessionsService,
                qaMessagesService,
                qaRetrievalLogsService,
                knowledgeBasesService,
                usersService,
                qaTaskWorker,
                buildTaskPolicyProperties()
        );

        QaSessions session = new QaSessions();
        session.setId(5L);

        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(9001L);
        task.setSessionId(5L);
        task.setUserMessageId(101L);
        task.setTaskStatus("running");
        task.setProgressStage("running");
        task.setQueryMode("drift");
        task.setRequestedMode("smart");
        task.setResolvedMode("drift");
        task.setQueryText("请用 drift 模式回答");
        task.setLatestLogs("""
                [
                  {
                    "type": "reduce_started",
                    "mode": "drift",
                    "summary": "正在综合 DRIFT 检索到的课程上下文。",
                    "metrics": {},
                    "evidence": [],
                    "eventSeq": 18
                  }
                ]
                """);
        task.setPartialResponseText("drift 已生成的部分回答");
        task.setStreamEventSeq(18L);
        task.setMemoryApplied(true);
        task.setMemoryStrategy("local_history");
        task.setMemoryScope("userId=7;courseId=os;knowledgeBaseId=3;indexRunId=17");
        task.setMemorySourceCount(2);
        task.setMemorySizeChars(88);
        task.setContextCharCount(120);
        task.setContextTokenCount(31);
        task.setContextTokenizer("jtokkit:o200k_base");

        given(qaSessionsService.getRequiredById(5L)).willReturn(session);
        given(qaRetrievalLogsService.getRequiredTask(5L, 9001L)).willReturn(task);

        QaTaskDetailResponse response = workflowService.getTaskDetail(5L, 9001L);

        assertThat(response.getMode()).isEqualTo("drift");
        assertThat(response.getRequestedMode()).isEqualTo("smart");
        assertThat(response.getResolvedMode()).isEqualTo("drift");
        assertThat(response.getRecommendedPollingIntervalSeconds()).isEqualTo(15);
        assertThat(response.getStaleTimeoutSeconds()).isEqualTo(1800);
        assertThat(response.getTimeoutMessage()).contains("drift");
        assertThat(response.getMemoryApplied()).isTrue();
        assertThat(response.getMemoryStrategy()).isEqualTo("local_history");
        assertThat(response.getMemoryScope()).contains("knowledgeBaseId=3");
        assertThat(response.getMemorySourceCount()).isEqualTo(2);
        assertThat(response.getMemorySizeEstimate()).isEqualTo(88);
        assertThat(response.getContextSizeEstimate().getChars()).isEqualTo(120);
        assertThat(response.getContextSizeEstimate().getTokens()).isEqualTo(31);
        assertThat(response.getContextSizeEstimate().getTokenizer()).isEqualTo("jtokkit:o200k_base");
        assertThat(response.getPartialResponseText()).isEqualTo("drift 已生成的部分回答");
        assertThat(response.getStreamEventSeq()).isEqualTo(18L);
        assertThat(response.getLatestLogs()).containsExactly("正在综合 DRIFT 检索到的课程上下文。");
        assertThat(response.getProgressEvents()).hasSize(1);
        assertThat(response.getProgressEvents().get(0).getType()).isEqualTo("reduce_started");
        assertThat(response.getProgressEvents().get(0).getEventSeq()).isEqualTo(18L);

        task.setRequestedMode(null);
        task.setResolvedMode(null);
        QaTaskDetailResponse legacyResponse = workflowService.getTaskDetail(5L, 9001L);

        assertThat(legacyResponse.getRequestedMode()).isEqualTo("drift");
        assertThat(legacyResponse.getResolvedMode()).isEqualTo("drift");
    }

    private KnowledgeBases buildKnowledgeBase() {
        KnowledgeBases knowledgeBase = new KnowledgeBases();
        knowledgeBase.setId(3L);
        knowledgeBase.setCourseId("os");
        knowledgeBase.setActiveIndexRunId(17L);
        return knowledgeBase;
    }

    private QaSessions formalSession() {
        QaSessions session = new QaSessions();
        session.setId(5L);
        session.setUserId(7L);
        session.setStatus("active");
        session.setKnowledgeBaseId(3L);
        session.setCourseId("os");
        session.setSessionType("formal");
        session.setIndexRunId(23L);
        return session;
    }

    private QaRetrievalLogs pendingTask(Long id) {
        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setId(id);
        task.setTaskStatus("pending");
        task.setProgressStage("queued");
        task.setMemoryApplied(false);
        task.setMemoryStrategy("none");
        return task;
    }

    private QaMessages message(Long id, Long sessionId, String role, int sequenceNo, String content) {
        QaMessages message = new QaMessages();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setSequenceNo(sequenceNo);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.of(2026, 5, 17, 12, sequenceNo));
        return message;
    }

    private CkqaIntegrationProperties buildTaskPolicyProperties() {
        CkqaIntegrationProperties properties = new CkqaIntegrationProperties();
        properties.getPolling().getQueryTaskModeIntervalSeconds().put("drift", 15L);
        properties.getTimeout().getQueryTaskModeStaleSeconds().put("drift", 1800L);
        return properties;
    }

    private AuthenticatedUser authenticatedStudent() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
