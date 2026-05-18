package org.ysu.ckqaback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.entity.QaRetrievalHits;
import org.ysu.ckqaback.entity.QaRetrievalLogs;
import org.ysu.ckqaback.entity.QaSourceReviews;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.QaSourceReviewsMapper;
import org.ysu.ckqaback.qa.dto.QaSourceReviewResponse;
import org.ysu.ckqaback.qa.dto.UpsertQaSourceReviewRequest;
import org.ysu.ckqaback.service.QaRetrievalHitsService;
import org.ysu.ckqaback.service.QaRetrievalLogsService;
import org.ysu.ckqaback.service.QaSourceReviewsService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 问答来源人工标注服务实现。
 */
@Service
public class QaSourceReviewsServiceImpl extends ServiceImpl<QaSourceReviewsMapper, QaSourceReviews>
        implements QaSourceReviewsService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> RELEVANCE = Set.of("relevant", "partially_relevant", "irrelevant", "unknown");
    private static final Set<String> CITATION_QUALITY = Set.of(
            "supports_claim",
            "weak_support",
            "wrong_source",
            "duplicate",
            "unknown"
    );

    private final QaRetrievalHitsService retrievalHitsService;
    private final QaRetrievalLogsService retrievalLogsService;
    private final CourseAccessService courseAccessService;

    public QaSourceReviewsServiceImpl(
            QaRetrievalHitsService retrievalHitsService,
            QaRetrievalLogsService retrievalLogsService,
            CourseAccessService courseAccessService
    ) {
        this.retrievalHitsService = retrievalHitsService;
        this.retrievalLogsService = retrievalLogsService;
        this.courseAccessService = courseAccessService;
    }

    @Override
    public QaSourceReviewResponse upsertReview(
            Long retrievalHitId,
            UpsertQaSourceReviewRequest request,
            AuthenticatedUser currentUser
    ) {
        requireOpsUser(currentUser);
        String relevance = normalize(request.getRelevance(), RELEVANCE, "来源相关性不合法");
        String citationQuality = normalize(request.getCitationQuality(), CITATION_QUALITY, "引用质量不合法");
        QaRetrievalHits hit = retrievalHitsService.getById(retrievalHitId);
        if (hit == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "来源不存在");
        }
        QaRetrievalLogs task = retrievalLogsService.getById(hit.getRetrievalLogId());
        if (task == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "来源检索任务不存在");
        }
        assertTaskReadable(task, currentUser);

        LocalDateTime now = LocalDateTime.now(SHANGHAI_ZONE);
        QaSourceReviews review = getOne(new LambdaQueryWrapper<QaSourceReviews>()
                .eq(QaSourceReviews::getRetrievalHitId, retrievalHitId)
                .eq(QaSourceReviews::getReviewerUserId, currentUser.id())
                .last("LIMIT 1"), false);
        if (review == null) {
            review = new QaSourceReviews();
            review.setRetrievalHitId(retrievalHitId);
            review.setRetrievalLogId(hit.getRetrievalLogId());
            review.setReviewerUserId(currentUser.id());
            review.setCreatedAt(now);
        }
        review.setRelevance(relevance);
        review.setCitationQuality(citationQuality);
        review.setNote(normalizeNote(request.getNote()));
        review.setUpdatedAt(now);
        saveOrUpdate(review);
        return QaSourceReviewResponse.fromEntity(review);
    }

    @Override
    public Map<Long, List<QaSourceReviewResponse>> findReviewsByHitIds(List<Long> retrievalHitIds) {
        if (CollectionUtils.isEmpty(retrievalHitIds)) {
            return Map.of();
        }
        return list(new LambdaQueryWrapper<QaSourceReviews>()
                .in(QaSourceReviews::getRetrievalHitId, retrievalHitIds)
                .orderByAsc(QaSourceReviews::getRetrievalHitId)
                .orderByAsc(QaSourceReviews::getId))
                .stream()
                .collect(Collectors.groupingBy(
                        QaSourceReviews::getRetrievalHitId,
                        java.util.LinkedHashMap::new,
                        Collectors.mapping(QaSourceReviewResponse::fromEntity, Collectors.toList())
                ));
    }

    private void assertTaskReadable(QaRetrievalLogs task, AuthenticatedUser currentUser) {
        if (isAdmin(currentUser)) {
            return;
        }
        if (!StringUtils.hasText(task.getCourseId())) {
            throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "无问答运维权限");
        }
        courseAccessService.assertCourseReadable(task.getCourseId(), currentUser.userCode());
    }

    private void requireOpsUser(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        if (isAdmin(currentUser) || hasRole(currentUser, "teacher") || hasRole(currentUser, "assistant")
                || hasPermission(currentUser, "qa:log:read")) {
            return;
        }
        throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "无问答运维权限");
    }

    private boolean isAdmin(AuthenticatedUser user) {
        return hasRole(user, "admin") || hasPermission(user, "*");
    }

    private boolean hasRole(AuthenticatedUser user, String role) {
        return user != null && user.roles() != null && user.roles().stream().anyMatch(role::equalsIgnoreCase);
    }

    private boolean hasPermission(AuthenticatedUser user, String permission) {
        return user != null && user.permissions() != null && user.permissions().contains(permission);
    }

    private String normalize(String value, Set<String> allowed, String message) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        if (!allowed.contains(normalized)) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeNote(String note) {
        if (!StringUtils.hasText(note)) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
