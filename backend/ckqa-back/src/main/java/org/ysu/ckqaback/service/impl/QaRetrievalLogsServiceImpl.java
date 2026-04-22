package org.ysu.ckqaback.service.impl;

import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.mapper.QaRetrievalLogsMapper;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

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
    public QaRetrievalLogs createSuccessLog(Long sessionId, String courseId, Long indexRunId, String mode, String queryText) {
        return createLog(sessionId, courseId, indexRunId, mode, queryText, "success", null);
    }

    @Override
    public QaRetrievalLogs createFailureLog(
            Long sessionId,
            String courseId,
            Long indexRunId,
            String mode,
            String queryText,
            String errorMessage
    ) {
        return createLog(sessionId, courseId, indexRunId, mode, queryText, "failed", errorMessage);
    }

    private QaRetrievalLogs createLog(
            Long sessionId,
            String courseId,
            Long indexRunId,
            String mode,
            String queryText,
            String retrievalStatus,
            String errorMessage
    ) {
        QaRetrievalLogs log = new QaRetrievalLogs();
        log.setSessionId(sessionId);
        log.setCourseId(courseId);
        log.setIndexRunId(indexRunId);
        log.setQueryMode(mode);
        log.setQueryText(queryText);
        log.setRetrievalStatus(retrievalStatus);
        log.setErrorMessage(errorMessage);
        save(log);
        return log;
    }
}
