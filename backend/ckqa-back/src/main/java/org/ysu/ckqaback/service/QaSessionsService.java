package org.ysu.ckqaback.service;

import org.ysu.ckqaback.entity.QaSessions;
import com.baomidou.mybatisplus.extension.service.IService;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaSessionQueryRequest;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;
import org.ysu.ckqaback.qa.dto.QaSessionStatsResponse;

import java.time.LocalDateTime;

/**
 * <p>
 * 问答会话表 服务类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
public interface QaSessionsService extends IService<QaSessions> {

    QaSessions getRequiredById(Long id);

    QaSessions createSession(CreateQaSessionRequest request);

    QaSessions createSession(CreateQaSessionRequest request, Long indexRunId, LocalDateTime indexLockedAt);

    QaSessions createForkSession(
            QaSessions parent,
            Long forkedFromMessageId,
            Integer forkedFromSequenceNo,
            String title,
            String forkReason
    );

    ApiPageData<QaSessionResponse> pageFormalSessions(Long userId, QaSessionQueryRequest request);

    QaSessionStatsResponse statsFormalSessions(Long userId, QaSessionQueryRequest request);

    void lockIndexRun(Long id, Long indexRunId, LocalDateTime indexLockedAt);

    void touchLastMessageAt(Long id);

    QaSessions updateSession(Long id, String title, String status, Boolean isFavorite);
}
