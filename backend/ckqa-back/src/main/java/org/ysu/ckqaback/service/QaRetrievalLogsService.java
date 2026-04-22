package org.ysu.ckqaback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.integration.graphrag.GraphRagTaskSnapshot;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 问答检索日志表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface QaRetrievalLogsService extends IService<QaRetrievalLogs> {

    QaRetrievalLogs createPendingTask(
            Long sessionId,
            String courseId,
            Long indexRunId,
            Long userMessageId,
            String mode,
            String queryText
    );

    QaRetrievalLogs getRequiredTask(Long sessionId, Long taskId);

    void bindPythonTask(Long taskId, String pythonTaskId, String taskStatus, String progressStage);

    void syncRunningSnapshot(Long taskId, GraphRagTaskSnapshot snapshot);

    void markSuccess(Long taskId, Long assistantMessageId, String latestLogs, String retrievalStatus);

    void markFailed(Long taskId, String taskStatus, String errorMessage, String latestLogs);

    Map<Long, QaRetrievalLogs> findLatestByUserMessageIds(List<Long> userMessageIds);
}
