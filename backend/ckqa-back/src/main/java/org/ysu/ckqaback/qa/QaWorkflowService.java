package org.ysu.ckqaback.qa;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties.QueryTaskModePolicy;
import org.ysu.ckqaback.qa.context.QaContextAssembler;
import org.ysu.ckqaback.qa.context.QaContextAssembly;
import org.ysu.ckqaback.qa.context.QaQuestionRewriteResult;
import org.ysu.ckqaback.qa.context.QaQuestionRewriteService;
import org.ysu.ckqaback.qa.context.QaRetrievalLogContext;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.ContextSizeEstimateResponse;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaSessionQueryRequest;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;
import org.ysu.ckqaback.service.UsersService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 问答业务工作流服务。
 */
@Service
@RequiredArgsConstructor
public class QaWorkflowService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    private final QaSessionsService qaSessionsService;
    private final QaMessagesService qaMessagesService;
    private final QaRetrievalLogsService qaRetrievalLogsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final UsersService usersService;
    private final QaTaskWorker qaTaskWorker;
    private final CkqaIntegrationProperties properties;
    private final QaContextAssembler qaContextAssembler = new QaContextAssembler();
    private final QaQuestionRewriteService qaQuestionRewriteService = new QaQuestionRewriteService();

    public QaSessionResponse createSession(CreateQaSessionRequest request) {
        usersService.getRequiredById(request.getUserId());
        Long lockedIndexRunId = null;
        LocalDateTime indexLockedAt = null;
        if (request.getKnowledgeBaseId() != null) {
            KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(request.getKnowledgeBaseId());
            if (!"smoke".equals(request.getSessionType())) {
                lockedIndexRunId = knowledgeBase.getActiveIndexRunId();
                if (lockedIndexRunId == null) {
                    throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT);
                }
                indexLockedAt = LocalDateTime.now(SHANGHAI_ZONE);
            }
        }
        return QaSessionResponse.fromEntity(qaSessionsService.createSession(request, lockedIndexRunId, indexLockedAt));
    }

    public ApiPageData<QaSessionResponse> listSessions(Long currentUserId, QaSessionQueryRequest request) {
        if (currentUserId == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        usersService.getRequiredById(currentUserId);
        return qaSessionsService.pageFormalSessions(currentUserId, request);
    }

    public QaTaskSubmissionResponse sendMessage(Long sessionId, CreateQaMessageRequest request) {
        return sendMessage(sessionId, request, null);
    }

    public QaTaskSubmissionResponse sendMessage(Long sessionId, CreateQaMessageRequest request, Long indexRunIdOverride) {
        QaSessions session = qaSessionsService.getRequiredById(sessionId);
        if (!"active".equals(session.getStatus())) {
            throw new BusinessException(ApiResultCode.QA_SESSION_NOT_ACTIVE, HttpStatus.CONFLICT);
        }
        if (session.getKnowledgeBaseId() == null) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT, "问答会话未绑定知识库");
        }

        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(session.getKnowledgeBaseId());
        Long indexRunId = resolveSessionIndexRunId(session, knowledgeBase, indexRunIdOverride);

        List<QaMessages> history = qaMessagesService.listBySessionId(sessionId);
        QaContextAssembly context = qaContextAssembler.assemble(request.getMode(), request.getContent(), history);
        QaQuestionRewriteResult rewrite = qaQuestionRewriteService.rewrite(request.getMode(), request.getContent(), context);
        QaRetrievalLogContext logContext = new QaRetrievalLogContext(
                request.getContent(),
                rewrite.retrievalQueryText(),
                context.snapshotText(),
                context.strategy(),
                context.messageRange(),
                context.charCount(),
                rewrite.rewriteApplied(),
                rewrite.rewriteReason(),
                rewrite.rewriteSourceMessageRange()
        );

        QaMessages userMessage = qaMessagesService.appendUserMessage(sessionId, request.getContent());
        qaSessionsService.touchLastMessageAt(sessionId);
        QaRetrievalLogs task = qaRetrievalLogsService.createPendingTask(
                sessionId,
                session.getCourseId(),
                indexRunId,
                userMessage.getId(),
                request.getMode(),
                rewrite.retrievalQueryText(),
                logContext
        );
        dispatchAfterCommit(sessionId, task.getId());
        QueryTaskModePolicy taskPolicy = properties.resolveQueryTaskModePolicy(request.getMode());

        return QaTaskSubmissionResponse.of(
                QaMessageResponse.fromEntity(userMessage),
                task.getId(),
                task.getTaskStatus(),
                task.getProgressStage(),
                null,
                task.getCreatedAt(),
                taskPolicy.mode(),
                taskPolicy.recommendedPollingIntervalSeconds(),
                taskPolicy.staleTimeoutSeconds(),
                taskPolicy.timeoutMessage(),
                context.contextApplied(),
                context.strategy(),
                ContextSizeEstimateResponse.of(context.charCount())
        );
    }

    private Long resolveSessionIndexRunId(QaSessions session, KnowledgeBases knowledgeBase, Long indexRunIdOverride) {
        if (indexRunIdOverride != null) {
            return indexRunIdOverride;
        }
        if ("smoke".equals(session.getSessionType())) {
            Long activeIndexRunId = knowledgeBase.getActiveIndexRunId();
            if (activeIndexRunId == null) {
                throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT);
            }
            return activeIndexRunId;
        }
        if (session.getIndexRunId() != null) {
            return session.getIndexRunId();
        }

        List<Long> historicalIndexRunIds = qaRetrievalLogsService.findDistinctSuccessfulIndexRunIdsBySession(session.getId());
        if (historicalIndexRunIds.size() == 1) {
            Long recoveredIndexRunId = historicalIndexRunIds.get(0);
            LocalDateTime lockedAt = LocalDateTime.now(SHANGHAI_ZONE);
            qaSessionsService.lockIndexRun(session.getId(), recoveredIndexRunId, lockedAt);
            session.setIndexRunId(recoveredIndexRunId);
            session.setIndexLockedAt(lockedAt);
            return recoveredIndexRunId;
        }

        throw new BusinessException(
                ApiResultCode.KNOWLEDGE_BASE_NOT_READY,
                HttpStatus.CONFLICT,
                "该会话创建于索引版本固化前，请基于当前索引新建会话"
        );
    }

    private void dispatchAfterCommit(Long sessionId, Long taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            qaTaskWorker.dispatch(sessionId, taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                qaTaskWorker.dispatch(sessionId, taskId);
            }
        });
    }

    public QaSessionResponse getSession(Long id) {
        return QaSessionResponse.fromEntity(qaSessionsService.getRequiredById(id));
    }

    public List<QaMessageResponse> listMessages(Long sessionId) {
        qaSessionsService.getRequiredById(sessionId);
        List<QaMessages> messages = qaMessagesService.listBySessionId(sessionId);
        List<Long> userMessageIds = messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(QaMessages::getId)
                .toList();
        Map<Long, QaRetrievalLogs> taskMap = qaRetrievalLogsService.findLatestByUserMessageIds(userMessageIds);

        return messages.stream()
                .map(message -> {
                    QaRetrievalLogs task = "user".equals(message.getRole()) ? taskMap.get(message.getId()) : null;
                    return QaMessageResponse.of(
                            message.getId(),
                            message.getSessionId(),
                            message.getRole(),
                            message.getSequenceNo(),
                            message.getContent(),
                            message.getCreatedAt(),
                            task == null ? null : task.getTaskStatus(),
                            task == null ? null : task.getProgressStage()
                    );
                })
                .toList();
    }

    public QaTaskDetailResponse getTaskDetail(Long sessionId, Long taskId) {
        qaSessionsService.getRequiredById(sessionId);
        QaRetrievalLogs task = qaRetrievalLogsService.getRequiredTask(sessionId, taskId);

        QaMessageResponse assistantMessage = null;
        if (task.getAssistantMessageId() != null) {
            QaMessages message = qaMessagesService.getById(task.getAssistantMessageId());
            assistantMessage = message == null ? null : QaMessageResponse.fromEntity(message);
        }

        List<String> latestLogs = StringUtils.hasText(task.getLatestLogs())
                ? Arrays.stream(task.getLatestLogs().split("\\R")).toList()
                : List.of();
        QueryTaskModePolicy taskPolicy = properties.resolveQueryTaskModePolicy(task.getQueryMode());
        String contextStrategy = StringUtils.hasText(task.getContextStrategy()) ? task.getContextStrategy() : "none";

        return QaTaskDetailResponse.of(
                task.getId(),
                task.getUserMessageId(),
                task.getAssistantMessageId(),
                task.getTaskStatus(),
                task.getProgressStage(),
                task.getRetrievalStatus(),
                task.getQueryMode(),
                task.getQueryText(),
                latestLogs,
                task.getStartedAt(),
                task.getLastHeartbeatAt(),
                task.getFinishedAt(),
                assistantMessage,
                task.getErrorMessage(),
                taskPolicy.recommendedPollingIntervalSeconds(),
                taskPolicy.staleTimeoutSeconds(),
                taskPolicy.timeoutMessage(),
                !"none".equals(contextStrategy),
                contextStrategy,
                ContextSizeEstimateResponse.of(task.getContextCharCount())
        );
    }
}
