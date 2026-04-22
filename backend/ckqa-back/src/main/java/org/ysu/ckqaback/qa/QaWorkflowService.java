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
import org.ysu.ckqaback.integration.graphrag.GraphRagChatResult;
import org.ysu.ckqaback.integration.graphrag.GraphRagQueryClient;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaRoundResponse;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;
import org.ysu.ckqaback.service.KnowledgeBasesService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;
import org.ysu.ckqaback.service.UsersService;

import java.util.List;

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
    private final GraphRagQueryClient graphRagQueryClient;

    public QaSessionResponse createSession(CreateQaSessionRequest request) {
        usersService.getRequiredById(request.getUserId());
        if (request.getKnowledgeBaseId() != null) {
            knowledgeBasesService.getRequiredById(request.getKnowledgeBaseId());
        }
        return QaSessionResponse.fromEntity(qaSessionsService.createSession(request));
    }

    public QaRoundResponse sendMessage(Long sessionId, CreateQaMessageRequest request) {
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
        try {
            GraphRagChatResult result = graphRagQueryClient.query(request.getMode(), request.getContent());
            QaRetrievalLogs retrievalLog = qaRetrievalLogsService.createSuccessLog(
                    sessionId,
                    session.getCourseId(),
                    knowledgeBase.getActiveIndexRunId(),
                    request.getMode(),
                    request.getContent()
            );
            QaMessages assistantMessage = qaMessagesService.appendAssistantMessage(sessionId, result.content());
            qaSessionsService.touchLastMessageAt(sessionId);
            return QaRoundResponse.of(
                    QaMessageResponse.fromEntity(userMessage),
                    QaMessageResponse.fromEntity(assistantMessage),
                    retrievalLog.getRetrievalStatus()
            );
        } catch (RuntimeException exception) {
            qaRetrievalLogsService.createFailureLog(
                    sessionId,
                    session.getCourseId(),
                    knowledgeBase.getActiveIndexRunId(),
                    request.getMode(),
                    request.getContent(),
                    shortenMessage(exception.getMessage(), "GraphRAG调用失败")
            );
            throw exception;
        }
    }

    public QaSessionResponse getSession(Long id) {
        return QaSessionResponse.fromEntity(qaSessionsService.getRequiredById(id));
    }

    public List<QaMessageResponse> listMessages(Long sessionId) {
        qaSessionsService.getRequiredById(sessionId);
        return qaMessagesService.listBySessionId(sessionId).stream()
                .map(QaMessageResponse::fromEntity)
                .toList();
    }

    private String shortenMessage(String rawMessage, String fallback) {
        if (!StringUtils.hasText(rawMessage)) {
            return fallback;
        }
        return rawMessage.length() > 500 ? rawMessage.substring(0, 500) : rawMessage;
    }
}
