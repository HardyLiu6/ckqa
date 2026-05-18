package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.entity.QaMessageFeedback;
import org.ysu.ckqaback.entity.QaMessages;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.QaMessageFeedbackMapper;
import org.ysu.ckqaback.qa.dto.QaFeedbackResponse;
import org.ysu.ckqaback.qa.dto.SubmitQaFeedbackRequest;
import org.ysu.ckqaback.service.QaMessageFeedbackService;
import org.ysu.ckqaback.service.QaMessagesService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSessionsService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 问答消息反馈服务实现。
 */
@Service
public class QaMessageFeedbackServiceImpl extends ServiceImpl<QaMessageFeedbackMapper, QaMessageFeedback>
        implements QaMessageFeedbackService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> RATINGS = Set.of("helpful", "unhelpful", "needs_improvement");
    private static final Set<String> TAGS = Set.of(
            "source_irrelevant",
            "too_long",
            "wants_example",
            "unclear",
            "incorrect",
            "other"
    );
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final QaMessagesService messagesService;
    private final QaSessionsService sessionsService;
    private final QaRetrievalLogsService retrievalLogsService;

    public QaMessageFeedbackServiceImpl(
            QaMessagesService messagesService,
            QaSessionsService sessionsService,
            QaRetrievalLogsService retrievalLogsService
    ) {
        this.messagesService = messagesService;
        this.sessionsService = sessionsService;
        this.retrievalLogsService = retrievalLogsService;
    }

    @Override
    public QaFeedbackResponse upsertFeedback(SubmitQaFeedbackRequest request, AuthenticatedUser currentUser) {
        Long userId = requireUserId(currentUser);
        String rating = normalizeRating(request.getRating());
        List<String> tags = normalizeTags(request.getTags());
        String comment = normalizeComment(request.getComment());
        FeedbackScope scope = resolveFeedbackScope(request.getMessageId(), userId);

        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        QaMessageFeedback feedback = getOne(new LambdaQueryWrapper<QaMessageFeedback>()
                .eq(QaMessageFeedback::getUserId, userId)
                .eq(QaMessageFeedback::getMessageId, request.getMessageId())
                .last("LIMIT 1"), false);
        if (feedback == null) {
            feedback = new QaMessageFeedback();
            feedback.setMessageId(scope.message().getId());
            feedback.setRetrievalLogId(scope.task().getId());
            feedback.setSessionId(scope.session().getId());
            feedback.setUserId(userId);
            feedback.setCourseId(scope.session().getCourseId());
            feedback.setKnowledgeBaseId(scope.session().getKnowledgeBaseId());
            feedback.setCreatedAt(now);
        }
        feedback.setRating(rating);
        feedback.setTags(writeTags(tags));
        feedback.setComment(comment);
        feedback.setUpdatedAt(now);
        saveOrUpdate(feedback);
        return QaFeedbackResponse.fromEntity(feedback, tags);
    }

    @Override
    public void deleteFeedback(Long messageId, AuthenticatedUser currentUser) {
        Long userId = requireUserId(currentUser);
        resolveFeedbackScope(messageId, userId);
        remove(new LambdaUpdateWrapper<QaMessageFeedback>()
                .eq(QaMessageFeedback::getUserId, userId)
                .eq(QaMessageFeedback::getMessageId, messageId));
    }

    @Override
    public Map<Long, QaFeedbackResponse> findFeedbackByMessageIdsForUser(List<Long> messageIds, Long userId) {
        if (CollectionUtils.isEmpty(messageIds) || userId == null) {
            return Map.of();
        }
        return list(new LambdaQueryWrapper<QaMessageFeedback>()
                .in(QaMessageFeedback::getMessageId, messageIds)
                .eq(QaMessageFeedback::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(
                        QaMessageFeedback::getMessageId,
                        feedback -> QaFeedbackResponse.fromEntity(feedback, readTags(feedback.getTags())),
                        (left, right) -> left
                ));
    }

    private FeedbackScope resolveFeedbackScope(Long messageId, Long userId) {
        QaMessages message = messagesService.getById(messageId);
        if (message == null || !"assistant".equals(message.getRole())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "只能反馈助手回答");
        }
        QaSessions session = sessionsService.getRequiredById(message.getSessionId());
        if (!userId.equals(session.getUserId())) {
            throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "只能反馈自己的问答消息");
        }
        if (!"formal".equals(session.getSessionType())) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "只能反馈正式问答会话");
        }
        QaRetrievalLogs task = retrievalLogsService.getOne(new LambdaQueryWrapper<QaRetrievalLogs>()
                .eq(QaRetrievalLogs::getAssistantMessageId, message.getId())
                .last("LIMIT 1"), false);
        if (task == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.CONFLICT, "该回答缺少检索任务记录，暂不能反馈");
        }
        return new FeedbackScope(message, session, task);
    }

    private Long requireUserId(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        return currentUser.id();
    }

    private String normalizeRating(String rating) {
        String normalized = StringUtils.hasText(rating) ? rating.trim() : "";
        if (!RATINGS.contains(normalized)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "反馈结论不合法");
        }
        return normalized;
    }

    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawTag : rawTags) {
            String tag = StringUtils.hasText(rawTag) ? rawTag.trim() : "";
            if (!TAGS.contains(tag)) {
                throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "反馈标签不合法");
            }
            normalized.add(tag);
        }
        return List.copyOf(normalized);
    }

    private String normalizeComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private String writeTags(List<String> tags) {
        try {
            return OBJECT_MAPPER.writeValueAsString(tags == null ? List.of() : tags);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, "反馈标签序列化失败");
        }
    }

    public static List<String> readTags(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private record FeedbackScope(QaMessages message, QaSessions session, QaRetrievalLogs task) {
    }
}
