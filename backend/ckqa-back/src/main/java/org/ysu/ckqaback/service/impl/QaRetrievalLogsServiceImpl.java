package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskSnapshot;
import org.ysu.ckqaback.mapper.QaRetrievalLogsMapper;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public QaRetrievalLogs createPendingTask(
            Long sessionId,
            String courseId,
            Long indexRunId,
            Long userMessageId,
            String mode,
            String queryText
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
                .set(QaRetrievalLogs::getLatestLogs, joinLatestLogs(snapshot.latestLogs()))
                .set(QaRetrievalLogs::getStartedAt, snapshot.startedAt())
                .set(QaRetrievalLogs::getLastHeartbeatAt, snapshot.lastHeartbeatAt())
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
                .set(QaRetrievalLogs::getErrorMessage, null)
                .set(QaRetrievalLogs::getFinishedAt, LocalDateTime.now());
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
                .set(QaRetrievalLogs::getFinishedAt, LocalDateTime.now());
        baseMapper.update(null, updateWrapper);
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

    private int nextTaskSeq(Long userMessageId) {
        LambdaQueryWrapper<QaRetrievalLogs> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QaRetrievalLogs::getUserMessageId, userMessageId)
                .orderByDesc(QaRetrievalLogs::getTaskSeq)
                .last("LIMIT 1");
        QaRetrievalLogs latestTask = getOne(queryWrapper, false);
        return latestTask == null || latestTask.getTaskSeq() == null ? 1 : latestTask.getTaskSeq() + 1;
    }

    private String joinLatestLogs(List<String> latestLogs) {
        if (latestLogs == null || latestLogs.isEmpty()) {
            return "";
        }
        return String.join("\n", latestLogs);
    }

    private String shortenMessage(String rawMessage) {
        if (!StringUtils.hasText(rawMessage)) {
            return "";
        }
        return rawMessage.length() > 500 ? rawMessage.substring(0, 500) : rawMessage;
    }
}
