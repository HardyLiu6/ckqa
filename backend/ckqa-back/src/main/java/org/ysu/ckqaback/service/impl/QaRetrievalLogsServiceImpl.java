package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskSnapshot;
import org.ysu.ckqaback.mapper.QaRetrievalLogsMapper;
import org.ysu.ckqaback.qa.context.QaRetrievalLogContext;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * <p>
 * 问答检索日志表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class QaRetrievalLogsServiceImpl extends ServiceImpl<QaRetrievalLogsMapper, QaRetrievalLogs> implements QaRetrievalLogsService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public QaRetrievalLogs createPendingTask(
            Long sessionId,
            String courseId,
            Long indexRunId,
            Long userMessageId,
            String mode,
            String queryText
    ) {
        return createPendingTask(sessionId, courseId, indexRunId, userMessageId, mode, queryText, null);
    }

    @Override
    public QaRetrievalLogs createPendingTask(
            Long sessionId,
            String courseId,
            Long indexRunId,
            Long userMessageId,
            String mode,
            String queryText,
            QaRetrievalLogContext context
    ) {
        QaRetrievalLogs task = new QaRetrievalLogs();
        task.setSessionId(sessionId);
        task.setUserMessageId(userMessageId);
        task.setTaskSeq(nextTaskSeq(userMessageId));
        task.setTaskStatus("pending");
        task.setProgressStage("queued");
        task.setCourseId(courseId);
        task.setIndexRunId(indexRunId);
        task.setQueryMode(mode);
        task.setQueryText(queryText);
        if (context != null) {
            task.setOriginalQueryText(context.originalQueryText());
            task.setRetrievalQueryText(context.retrievalQueryText());
            task.setStandaloneQueryText(context.standaloneQueryText());
            task.setContextSnapshotText(context.contextSnapshotText());
            task.setContextStrategy(context.contextStrategy());
            task.setContextMessageRange(context.contextMessageRange());
            task.setContextCharCount(context.contextCharCount());
            task.setRewriteApplied(context.rewriteApplied());
            task.setRewriteReason(context.rewriteReason());
            task.setRewriteSourceMessageRange(context.rewriteSourceMessageRange());
            task.setRewriteMethod(context.rewriteMethod());
            task.setRewriteModel(context.rewriteModel());
            task.setRewriteConfidence(context.rewriteConfidence());
            task.setContextSnapshotVersion(context.contextSnapshotVersion());
            task.setRequestedMode(context.requestedMode());
            task.setResolvedMode(context.resolvedMode());
            task.setResolvedTopic(context.resolvedTopic());
            task.setTopicSource(context.topicSource());
            task.setTopicConfidence(context.topicConfidence());
            task.setTopicStackJson(context.topicStackJson());
            task.setSemanticStateVersion(context.semanticStateVersion());
            task.setSemanticStateJson(context.semanticStateJson());
            task.setTopicEntityBindingApplied(context.topicEntityBindingApplied());
            task.setTopicEntityBindingStatus(context.topicEntityBindingStatus());
            task.setTopicEntityBindingStrategy(context.topicEntityBindingStrategy());
            task.setTopicEntityCandidateCount(context.topicEntityCandidateCount());
            task.setTopicEntityTopScore(context.topicEntityTopScore());
            task.setTopicEntitySelectedId(context.topicEntitySelectedId());
            task.setTopicEntitySelectedName(context.topicEntitySelectedName());
            task.setTopicEntitySelectedType(context.topicEntitySelectedType());
            task.setTopicEntityCandidatesJson(context.topicEntityCandidatesJson());
            task.setTopicEntityFallbackReason(context.topicEntityFallbackReason());
            task.setTopicEntityLookupDurationMs(context.topicEntityLookupDurationMs());
            task.setRoutingConfidence(context.routingConfidence());
            task.setRoutingConfidenceBand(context.routingConfidenceBand());
            task.setRoutingReviewPriority(context.routingReviewPriority());
            task.setRoutingSnapshotJson(context.routingSnapshotJson());
            task.setMemoryApplied(context.memoryApplied());
            task.setMemoryStrategy(context.memoryStrategy());
            task.setMemoryScope(context.memoryScope());
            task.setMemorySourceCount(context.memorySourceCount());
            task.setMemorySizeChars(context.memorySizeChars());
            task.setMemoryGovernanceVersion(context.memoryGovernanceVersion());
            task.setMemoryLongTermCount(context.memoryLongTermCount());
            task.setMemoryRecentHistoryCount(context.memoryRecentHistoryCount());
            task.setMemoryInjectionReason(context.memoryInjectionReason());
            task.setMemorySourcesJson(context.memorySourcesJson());
            task.setQueryEngineStrategy(context.queryEngineStrategy());
            task.setHistoryFallbackReason(context.historyFallbackReason());
            task.setMemoryHistoryJson(context.memoryHistoryJson());
        }
        task.setCreatedAt(LocalDateTime.now(SHANGHAI_ZONE));
        save(task);
        return task;
    }

    @Override
    public QaRetrievalLogs getRequiredTask(Long sessionId, Long taskId) {
        LambdaQueryWrapper<QaRetrievalLogs> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QaRetrievalLogs::getId, taskId)
                .eq(QaRetrievalLogs::getSessionId, sessionId)
                .last("LIMIT 1");
        QaRetrievalLogs task = getOne(queryWrapper, false);
        if (task == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "问答任务不存在");
        }
        return task;
    }

    @Override
    public void bindPythonTask(Long taskId, String pythonTaskId, String taskStatus, String progressStage) {
        LambdaUpdateWrapper<QaRetrievalLogs> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QaRetrievalLogs::getId, taskId)
                .set(QaRetrievalLogs::getPythonTaskId, pythonTaskId)
                .set(QaRetrievalLogs::getTaskStatus, taskStatus)
                .set(QaRetrievalLogs::getProgressStage, progressStage);
        baseMapper.update(null, updateWrapper);
    }

    @Override
    public void syncRunningSnapshot(Long taskId, GraphRagTaskSnapshot snapshot) {
        LambdaUpdateWrapper<QaRetrievalLogs> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QaRetrievalLogs::getId, taskId)
                .set(QaRetrievalLogs::getTaskStatus, snapshot.taskStatus())
                .set(QaRetrievalLogs::getProgressStage, snapshot.progressStage())
                .set(QaRetrievalLogs::getLatestLogs, serializeProgressEventsOrLogs(snapshot))
                .set(QaRetrievalLogs::getPartialResponseText, snapshot.partialResultText())
                .set(QaRetrievalLogs::getStreamEventSeq, snapshot.streamEventSeq())
                .set(QaRetrievalLogs::getStartedAt, snapshot.startedAt())
                .set(QaRetrievalLogs::getLastHeartbeatAt, snapshot.lastHeartbeatAt())
                .set(StringUtils.hasText(snapshot.queryEngineStrategy()), QaRetrievalLogs::getQueryEngineStrategy, snapshot.queryEngineStrategy())
                .set(StringUtils.hasText(snapshot.historyFallbackReason()), QaRetrievalLogs::getHistoryFallbackReason, snapshot.historyFallbackReason())
                .set(snapshot.finishedAt() != null, QaRetrievalLogs::getFinishedAt, snapshot.finishedAt());
        baseMapper.update(null, updateWrapper);
    }

    @Override
    public void markSuccess(Long taskId, Long assistantMessageId, String latestLogs, String retrievalStatus) {
        LambdaUpdateWrapper<QaRetrievalLogs> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QaRetrievalLogs::getId, taskId)
                .set(QaRetrievalLogs::getAssistantMessageId, assistantMessageId)
                .set(QaRetrievalLogs::getTaskStatus, "success")
                .set(QaRetrievalLogs::getProgressStage, "done")
                .set(QaRetrievalLogs::getRetrievalStatus, retrievalStatus)
                .set(QaRetrievalLogs::getLatestLogs, latestLogs)
                .set(QaRetrievalLogs::getPartialResponseText, null)
                .set(QaRetrievalLogs::getErrorMessage, null)
                .set(QaRetrievalLogs::getFinishedAt, LocalDateTime.now(SHANGHAI_ZONE));
        baseMapper.update(null, updateWrapper);
    }

    @Override
    public void markFailed(Long taskId, String taskStatus, String errorMessage, String latestLogs) {
        LambdaUpdateWrapper<QaRetrievalLogs> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(QaRetrievalLogs::getId, taskId)
                .set(QaRetrievalLogs::getTaskStatus, taskStatus)
                .set(QaRetrievalLogs::getProgressStage, "done")
                .set(QaRetrievalLogs::getRetrievalStatus, "failed")
                .set(QaRetrievalLogs::getErrorMessage, shortenMessage(errorMessage))
                .set(QaRetrievalLogs::getLatestLogs, latestLogs)
                .set(QaRetrievalLogs::getFinishedAt, LocalDateTime.now(SHANGHAI_ZONE));
        baseMapper.update(null, updateWrapper);
    }

    @Override
    public List<QaRetrievalLogs> recoverStaleActiveTasks(
            Function<String, Duration> staleThresholdResolver,
            Function<String, String> timeoutMessageResolver
    ) {
        LambdaQueryWrapper<QaRetrievalLogs> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(QaRetrievalLogs::getTaskStatus, List.of("pending", "running"))
                .orderByAsc(QaRetrievalLogs::getLastHeartbeatAt)
                .orderByAsc(QaRetrievalLogs::getCreatedAt);

        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        List<QaRetrievalLogs> staleTasks = list(queryWrapper).stream()
                .filter(task -> isStaleActiveTask(task, staleThresholdResolver.apply(task.getQueryMode()), now))
                .toList();

        for (QaRetrievalLogs task : staleTasks) {
            markFailed(
                    task.getId(),
                    "stale",
                    timeoutMessageResolver.apply(task.getQueryMode()),
                    task.getLatestLogs()
            );
        }
        return staleTasks;
    }

    @Override
    public Map<Long, QaRetrievalLogs> findLatestByUserMessageIds(List<Long> userMessageIds) {
        if (userMessageIds == null || userMessageIds.isEmpty()) {
            return Map.of();
        }

        LambdaQueryWrapper<QaRetrievalLogs> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(QaRetrievalLogs::getUserMessageId, userMessageIds)
                .orderByAsc(QaRetrievalLogs::getUserMessageId)
                .orderByDesc(QaRetrievalLogs::getTaskSeq)
                .orderByDesc(QaRetrievalLogs::getCreatedAt);

        Map<Long, QaRetrievalLogs> latestByUserMessage = new LinkedHashMap<>();
        for (QaRetrievalLogs task : list(queryWrapper)) {
            if (task.getUserMessageId() != null) {
                latestByUserMessage.putIfAbsent(task.getUserMessageId(), task);
            }
        }
        return latestByUserMessage;
    }

    @Override
    public List<Long> findDistinctSuccessfulIndexRunIdsBySession(Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        LambdaQueryWrapper<QaRetrievalLogs> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QaRetrievalLogs::getSessionId, sessionId)
                .eq(QaRetrievalLogs::getTaskStatus, "success")
                .isNotNull(QaRetrievalLogs::getIndexRunId)
                .select(QaRetrievalLogs::getIndexRunId);
        return list(queryWrapper).stream()
                .map(QaRetrievalLogs::getIndexRunId)
                .distinct()
                .toList();
    }

    private int nextTaskSeq(Long userMessageId) {
        LambdaQueryWrapper<QaRetrievalLogs> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QaRetrievalLogs::getUserMessageId, userMessageId)
                .orderByDesc(QaRetrievalLogs::getTaskSeq)
                .last("LIMIT 1");
        QaRetrievalLogs latestTask = getOne(queryWrapper, false);
        return latestTask == null || latestTask.getTaskSeq() == null ? 1 : latestTask.getTaskSeq() + 1;
    }

    private boolean isStaleActiveTask(QaRetrievalLogs task, Duration staleThreshold, LocalDateTime now) {
        if (!"pending".equals(task.getTaskStatus()) && !"running".equals(task.getTaskStatus())) {
            return false;
        }

        LocalDateTime referenceTime = task.getLastHeartbeatAt();
        if (referenceTime == null) {
            referenceTime = task.getStartedAt();
        }
        if (referenceTime == null) {
            referenceTime = task.getCreatedAt();
        }
        if (referenceTime == null) {
            return true;
        }
        return Duration.between(referenceTime, now).compareTo(staleThreshold) > 0;
    }

    private String joinLatestLogs(List<String> latestLogs) {
        if (latestLogs == null || latestLogs.isEmpty()) {
            return "";
        }
        return String.join("\n", latestLogs);
    }

    private String serializeProgressEventsOrLogs(GraphRagTaskSnapshot snapshot) {
        if (snapshot.progressEvents() != null && !snapshot.progressEvents().isEmpty()) {
            try {
                return OBJECT_MAPPER.writeValueAsString(snapshot.progressEvents());
            } catch (JsonProcessingException ex) {
                return joinLatestLogs(snapshot.latestLogs());
            }
        }
        return joinLatestLogs(snapshot.latestLogs());
    }

    private String shortenMessage(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return "";
        }
        return rawMessage.length() > 500 ? rawMessage.substring(0, 500) : rawMessage;
    }
}
