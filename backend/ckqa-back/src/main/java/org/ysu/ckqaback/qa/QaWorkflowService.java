package org.ysu.ckqaback.qa;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.KnowledgeBases;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties;
import org.ysu.ckqaback.integration.config.CkqaIntegrationProperties.QueryTaskModePolicy;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;
import org.ysu.ckqaback.service.UsersService;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 问答业务工作流服务。
 */
@Service
@RequiredArgsConstructor
public class QaWorkflowService {

    private final QaSessionsService qaSessionsService;
    private final QaMessagesService qaMessagesService;
    private final QaRetrievalLogsService qaRetrievalLogsService;
    private final KnowledgeBasesService knowledgeBasesService;
    private final UsersService usersService;
    private final QaTaskWorker qaTaskWorker;
    private final CkqaIntegrationProperties properties;

    public QaSessionResponse createSession(CreateQaSessionRequest request) {
        usersService.getRequiredById(request.getUserId());
        if (request.getKnowledgeBaseId() != null) {
            knowledgeBasesService.getRequiredById(request.getKnowledgeBaseId());
        }
        return QaSessionResponse.fromEntity(qaSessionsService.createSession(request));
    }

    public QaTaskSubmissionResponse sendMessage(Long sessionId, CreateQaMessageRequest request) {
        QaSessions session = qaSessionsService.getRequiredById(sessionId);
        if (!"active".equals(session.getStatus())) {
            throw new BusinessException(ApiResultCode.QA_SESSION_NOT_ACTIVE, HttpStatus.CONFLICT);
        }
        if (session.getKnowledgeBaseId() == null) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT, "问答会话未绑定知识库");
        }

        KnowledgeBases knowledgeBase = knowledgeBasesService.getRequiredById(session.getKnowledgeBaseId());
        if (knowledgeBase.getActiveIndexRunId() == null) {
            throw new BusinessException(ApiResultCode.KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT);
        }

        QaMessages userMessage = qaMessagesService.appendUserMessage(sessionId, request.getContent());
        qaSessionsService.touchLastMessageAt(sessionId);
        QaRetrievalLogs task = qaRetrievalLogsService.createPendingTask(
                sessionId,
                session.getCourseId(),
                knowledgeBase.getActiveIndexRunId(),
                userMessage.getId(),
                request.getMode(),
                request.getContent()
        );
        qaTaskWorker.dispatch(sessionId, task.getId());
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
                taskPolicy.timeoutMessage()
        );
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
                taskPolicy.timeoutMessage()
        );
    }
}
