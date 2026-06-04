package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.ysu.ckqaback.entity.QaSessions;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.QaSessionsMapper;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaSessionMessageCount;
import org.ysu.ckqaback.qa.dto.QaSessionQueryRequest;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;
import org.ysu.ckqaback.qa.dto.QaSessionStatsResponse;
import org.ysu.ckqaback.service.QaSessionsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * <p>
 * 问答会话表 服务实现类
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Service
public class QaSessionsServiceImpl extends ServiceImpl<QaSessionsMapper, QaSessions> implements QaSessionsService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public QaSessions getRequiredById(Long id) {
        QaSessions session = getById(id);
        if (session == null) {
            throw new BusinessException(ApiResultCode.QA_SESSION_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return session;
    }

    @Override
    public QaSessions createSession(CreateQaSessionRequest request) {
        return createSession(request, null, null);
    }

    @Override
    public QaSessions createSession(CreateQaSessionRequest request, Long indexRunId, LocalDateTime indexLockedAt) {
        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        QaSessions session = new QaSessions();
        session.setSessionCode(generateSessionCode());
        session.setUserId(request.getUserId());
        session.setCourseId(StringUtils.hasText(request.getCourseId()) ? request.getCourseId() : null);
        session.setKnowledgeBaseId(request.getKnowledgeBaseId());
        session.setIndexRunId(indexRunId);
        session.setIndexLockedAt(indexLockedAt);
        session.setSessionType(StringUtils.hasText(request.getSessionType()) ? request.getSessionType() : "formal");
        session.setTitle(StringUtils.hasText(request.getTitle()) ? request.getTitle() : "新建问答会话");
        session.setStatus("active");
        session.setIsFavorite(false);
        session.setCreatedAt(now);
        save(session);
        return session;
    }

    @Override
    public QaSessions createForkSession(
            QaSessions parent,
            Long forkedFromMessageId,
            Integer forkedFromSequenceNo,
            String title,
            String forkReason
    ) {
        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        QaSessions session = new QaSessions();
        session.setSessionCode(generateSessionCode());
        session.setUserId(parent.getUserId());
        session.setCourseId(parent.getCourseId());
        session.setCourseMembershipId(parent.getCourseMembershipId());
        session.setKnowledgeBaseId(parent.getKnowledgeBaseId());
        session.setIndexRunId(parent.getIndexRunId());
        session.setIndexLockedAt(parent.getIndexLockedAt());
        session.setSessionType("formal");
        session.setTitle(resolveForkTitle(parent.getTitle(), title));
        session.setStatus("active");
        session.setIsFavorite(false);
        session.setParentSessionId(parent.getId());
        session.setForkedFromMessageId(forkedFromMessageId);
        session.setForkedFromSequenceNo(forkedFromSequenceNo);
        session.setForkReason(StringUtils.hasText(forkReason) ? forkReason.trim() : null);
        session.setTranscriptVersion("v1");
        session.setCreatedAt(now);
        save(session);
        return session;
    }

    @Override
    public ApiPageData<QaSessionResponse> pageFormalSessions(Long userId, QaSessionQueryRequest request) {
        long current = request.getPage() == null ? 1L : request.getPage();
        long size = request.getSize() == null ? 20L : request.getSize();
        LambdaQueryWrapper<QaSessions> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaSessions::getUserId, userId)
                .eq(QaSessions::getSessionType, "formal")
                .eq(StringUtils.hasText(request.getCourseId()), QaSessions::getCourseId, request.getCourseId())
                .eq(request.getKnowledgeBaseId() != null, QaSessions::getKnowledgeBaseId, request.getKnowledgeBaseId())
                .eq(StringUtils.hasText(request.getStatus()), QaSessions::getStatus, request.getStatus())
                .eq(request.getFavorite() != null, QaSessions::getIsFavorite, request.getFavorite());
        wrapper.last(resolveSessionOrderBy(request.getSort()));

        IPage<QaSessions> page = page(new Page<>(current, size), wrapper);
        List<QaSessions> records = page.getRecords();
        Map<Long, Long> messageCountBySessionId = loadMessageCounts(records);
        return new ApiPageData<>(
                records.stream()
                        .map(session -> QaSessionResponse.fromEntity(
                                session,
                                messageCountBySessionId.getOrDefault(session.getId(), 0L)))
                        .toList(),
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getPages()
        );
    }

    @Override
    public QaSessionStatsResponse statsFormalSessions(Long userId, QaSessionQueryRequest request) {
        QaSessionStatsResponse stats = baseMapper.selectFormalSessionStats(
                userId,
                request.getStatus(),
                request.getCourseId(),
                request.getKnowledgeBaseId(),
                request.getFavorite()
        );
        return stats != null ? stats : new QaSessionStatsResponse(0L, 0L, 0L);
    }

    private String resolveSessionOrderBy(String sort) {
        if ("oldest".equals(sort)) {
            return " ORDER BY COALESCE(last_message_at, created_at) ASC, created_at ASC";
        }
        if ("messages".equals(sort)) {
            return """
                     ORDER BY (SELECT COUNT(*) FROM qa_messages m WHERE m.session_id = qa_sessions.id) DESC,
                     COALESCE(last_message_at, created_at) DESC,
                     created_at DESC
                    """;
        }
        // 无消息会话 last_message_at 为 NULL，MySQL 在 DESC 下会把它们排到最后，
        // 导致刚创建的会话被列表 size 截断而看不见。用 COALESCE 兜底到 created_at。
        return " ORDER BY COALESCE(last_message_at, created_at) DESC, created_at DESC";
    }

    private Map<Long, Long> loadMessageCounts(List<QaSessions> sessions) {
        List<Long> sessionIds = sessions.stream()
                .map(QaSessions::getId)
                .filter(id -> id != null)
                .toList();
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        return baseMapper.selectMessageCountsBySessionIds(sessionIds).stream()
                .filter(item -> item.getSessionId() != null)
                .collect(Collectors.toMap(
                        QaSessionMessageCount::getSessionId,
                        item -> item.getMessageCount() == null ? 0L : Math.max(0L, item.getMessageCount()),
                        Long::sum
                ));
    }

    @Override
    public void lockIndexRun(Long id, Long indexRunId, LocalDateTime indexLockedAt) {
        LambdaUpdateWrapper<QaSessions> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(QaSessions::getId, id)
                .isNull(QaSessions::getIndexRunId)
                .set(QaSessions::getIndexRunId, indexRunId)
                .set(QaSessions::getIndexLockedAt, indexLockedAt);
        baseMapper.update(null, wrapper);
    }

    @Override
    public void touchLastMessageAt(Long id) {
        LambdaUpdateWrapper<QaSessions> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(QaSessions::getId, id)
                .set(QaSessions::getLastMessageAt, LocalDateTime.now(SHANGHAI_ZONE));
        baseMapper.update(null, wrapper);
    }

    @Override
    public QaSessions updateSession(Long id, String title, String status, Boolean isFavorite) {
        QaSessions current = getRequiredById(id);
        String nextTitle = StringUtils.hasText(title) ? title.trim() : current.getTitle();
        String nextStatus = StringUtils.hasText(status) ? status.trim() : current.getStatus();
        Boolean nextFavorite = isFavorite == null ? Boolean.TRUE.equals(current.getIsFavorite()) : isFavorite;
        LambdaUpdateWrapper<QaSessions> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(QaSessions::getId, id)
                .set(QaSessions::getTitle, nextTitle)
                .set(QaSessions::getStatus, nextStatus)
                .set(QaSessions::getIsFavorite, nextFavorite)
                .set(QaSessions::getUpdatedAt, LocalDateTime.now(SHANGHAI_ZONE));
        baseMapper.update(null, wrapper);
        current.setTitle(nextTitle);
        current.setStatus(nextStatus);
        current.setIsFavorite(nextFavorite);
        current.setUpdatedAt(LocalDateTime.now(SHANGHAI_ZONE));
        return current;
    }

    private String generateSessionCode() {
        String code;
        do {
            code = "qa-" + UUID.randomUUID().toString().replace("-", "");
        } while (exists(new LambdaQueryWrapper<QaSessions>().eq(QaSessions::getSessionCode, code)));
        return code;
    }

    private String resolveForkTitle(String parentTitle, String requestedTitle) {
        if (StringUtils.hasText(requestedTitle)) {
            return requestedTitle.trim();
        }
        String baseTitle = StringUtils.hasText(parentTitle) ? parentTitle.trim() : "问答会话";
        String title = baseTitle + " 的分支";
        return title.length() > 255 ? title.substring(0, 255) : title;
    }
}
