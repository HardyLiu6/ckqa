package org.ysu.ckqaback.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthConstants;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.exception.GlobalExceptionHandler;
import org.ysu.ckqaback.qa.dto.ContextSizeEstimateResponse;
import org.ysu.ckqaback.qa.QaWorkflowService;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QaSessionsControllerWebMvcTest {

    private QaWorkflowService qaWorkflowService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        qaWorkflowService = Mockito.mock(QaWorkflowService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new QaSessionsController(qaWorkflowService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldListFormalSessionsForCurrentUserWithoutUserIdQueryParam() throws Exception {
        QaSessionResponse item = QaSessionResponse.of(
                5L,
                "qa-0001",
                7L,
                "os",
                3L,
                17L,
                LocalDateTime.of(2026, 5, 17, 10, 0),
                "formal",
                "操作系统问答",
                "active",
                LocalDateTime.of(2026, 5, 17, 10, 5),
                LocalDateTime.of(2026, 5, 17, 10, 0)
        );
        given(qaWorkflowService.listSessions(eq(7L), any())).willReturn(new ApiPageData<>(List.of(item), 1, 50, 1, 1));

        mockMvc.perform(get(ApiPaths.QA_SESSIONS)
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent())
                        .param("status", "active")
                        .param("page", "1")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(5))
                .andExpect(jsonPath("$.data.items[0].indexRunId").value(17))
                .andExpect(jsonPath("$.data.items[0].indexLockedAt").exists());

        then(qaWorkflowService).should().listSessions(eq(7L), argThat(request -> request.getPage() == 1L && request.getSize() == 50L));
    }

    @Test
    void shouldCreateSession() throws Exception {
        QaSessionResponse response = QaSessionResponse.of(
                5L,
                "qa-0001",
                7L,
                "os",
                3L,
                "smoke",
                "操作系统问答",
                "active",
                null,
                LocalDateTime.of(2026, 4, 21, 12, 0)
        );
        given(qaWorkflowService.createSession(any())).willReturn(response);

        mockMvc.perform(post(ApiPaths.QA_SESSIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 7,
                                  "courseId": "os",
                                  "knowledgeBaseId": 3,
                                  "sessionType": "smoke",
                                  "title": "操作系统问答"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.sessionType").value("smoke"))
                .andExpect(jsonPath("$.data.status").value("active"));

        then(qaWorkflowService).should().createSession(argThat(request -> "smoke".equals(request.getSessionType())));
    }

    @Test
    void shouldSubmitMessageAsAsyncTask() throws Exception {
        QaTaskSubmissionResponse response = QaTaskSubmissionResponse.of(
                QaMessageResponse.of(101L, 5L, "user", 1, "请概括这套图谱的主题", LocalDateTime.of(2026, 4, 22, 15, 20), null, null),
                9001L,
                "pending",
                "queued",
                null,
                LocalDateTime.of(2026, 4, 22, 15, 20, 31),
                "drift",
                15L,
                1800L,
                "drift 模式在真实环境里通常耗时更长，请按较低频率轮询并等待后台完成"
        );
        given(qaWorkflowService.sendMessage(eq(5L), any())).willReturn(response);

        mockMvc.perform(post(ApiPaths.QA_SESSIONS + "/5/messages")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "drift",
                                  "content": "请概括这套图谱的主题"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(9001))
                .andExpect(jsonPath("$.data.mode").value("drift"))
                .andExpect(jsonPath("$.data.taskStatus").value("pending"))
                .andExpect(jsonPath("$.data.recommendedPollingIntervalSeconds").value(15))
                .andExpect(jsonPath("$.data.staleTimeoutSeconds").value(1800))
                .andExpect(jsonPath("$.data.timeoutMessage").value("drift 模式在真实环境里通常耗时更长，请按较低频率轮询并等待后台完成"))
                .andExpect(jsonPath("$.data.assistantMessage").doesNotExist());
    }

    @Test
    void shouldReturnQaSessionNotActiveWhenSendingMessageToClosedSession() throws Exception {
        given(qaWorkflowService.sendMessage(eq(5L), any()))
                .willThrow(new BusinessException(ApiResultCode.QA_SESSION_NOT_ACTIVE, HttpStatus.CONFLICT));

        mockMvc.perform(post(ApiPaths.QA_SESSIONS + "/5/messages")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "drift",
                                  "content": "请概括这套图谱的主题"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4096))
                .andExpect(jsonPath("$.message").value("问答会话已关闭"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnKnowledgeBaseNotReadyWhenSessionKnowledgeBaseHasNoActiveIndex() throws Exception {
        given(qaWorkflowService.sendMessage(eq(5L), any()))
                .willThrow(new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT));

        mockMvc.perform(post(ApiPaths.QA_SESSIONS + "/5/messages")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "drift",
                                  "content": "请概括这套图谱的主题"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(4097))
                .andExpect(jsonPath("$.message").value("知识库当前没有可用索引"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldRejectArchivedFullMode() throws Exception {
        mockMvc.perform(post(ApiPaths.QA_SESSIONS + "/5/messages")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "mode": "full",
                                  "content": "请概括这套图谱的主题"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("参数校验失败"));
    }

    @Test
    void shouldGetTaskDetail() throws Exception {
        QaTaskDetailResponse response = QaTaskDetailResponse.of(
                9001L,
                101L,
                102L,
                "success",
                "done",
                "success",
                "global",
                "请概括这套图谱的主题",
                List.of("started graphrag query --method global", "done"),
                LocalDateTime.of(2026, 4, 22, 15, 20, 35),
                LocalDateTime.of(2026, 4, 22, 15, 21, 5),
                LocalDateTime.of(2026, 4, 22, 15, 22, 0),
                QaMessageResponse.of(102L, 5L, "assistant", 2, "图谱主题集中在操作系统概念网络", LocalDateTime.of(2026, 4, 22, 15, 22), null, null),
                null,
                5L,
                30L,
                "任务心跳超时",
                true,
                "recent",
                ContextSizeEstimateResponse.of(38)
        );
        given(qaWorkflowService.getTaskDetail(5L, 9001L)).willReturn(response);

        mockMvc.perform(get(ApiPaths.QA_SESSIONS + "/5/tasks/9001")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskStatus").value("success"))
                .andExpect(jsonPath("$.data.recommendedPollingIntervalSeconds").value(5))
                .andExpect(jsonPath("$.data.staleTimeoutSeconds").value(30))
                .andExpect(jsonPath("$.data.contextApplied").value(true))
                .andExpect(jsonPath("$.data.contextStrategy").value("recent"))
                .andExpect(jsonPath("$.data.contextSizeEstimate.chars").value(38))
                .andExpect(jsonPath("$.data.queryText").doesNotExist())
                .andExpect(jsonPath("$.data.latestLogs[0]").value("started graphrag query --method global"));
    }

    @Test
    void shouldListMessagesWithTaskSummaryOnlyOnUserMessages() throws Exception {
        given(qaWorkflowService.listMessages(5L)).willReturn(List.of(
                QaMessageResponse.of(101L, 5L, "user", 1, "请概括这套图谱的主题", LocalDateTime.of(2026, 4, 22, 15, 20), "running", "running"),
                QaMessageResponse.of(102L, 5L, "assistant", 2, "图谱主题集中在操作系统概念网络", LocalDateTime.of(2026, 4, 22, 15, 22), null, null)
        ));

        mockMvc.perform(get(ApiPaths.QA_SESSIONS + "/5/messages")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].taskStatus").value("running"))
                .andExpect(jsonPath("$.data[0].progressStage").value("running"))
                .andExpect(jsonPath("$.data[1].taskStatus").isEmpty());
    }

    @Test
    void shouldRequireAuthWhenReadingSessionDetails() throws Exception {
        mockMvc.perform(get(ApiPaths.QA_SESSIONS + "/5"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(4010))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void shouldRejectSessionAccessForNonOwner() throws Exception {
        Mockito.doThrow(new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "只能访问自己的问答会话"))
                .when(qaWorkflowService).ensureSessionOwner(5L, 7L);

        mockMvc.perform(get(ApiPaths.QA_SESSIONS + "/5/messages")
                        .requestAttr(AuthConstants.REQUEST_USER_ATTRIBUTE, authenticatedStudent()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(4030))
                .andExpect(jsonPath("$.message").value("只能访问自己的问答会话"));
    }

    private AuthenticatedUser authenticatedStudent() {
        return new AuthenticatedUser(7L, "student.zhouzh", "student.zhouzh", "周同学", List.of("student"), List.of());
    }
}
