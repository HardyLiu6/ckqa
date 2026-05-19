package org.ysu.ckqaback.qa;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.mapper.QaOperationsMapper;
import org.ysu.ckqaback.qa.dto.QaOperationFeedbackResponse;
import org.ysu.ckqaback.qa.dto.QaOperationLogDetailResponse;
import org.ysu.ckqaback.qa.dto.QaOperationLogResponse;
import org.ysu.ckqaback.qa.dto.QaOperationSourceResponse;
import org.ysu.ckqaback.qa.dto.QaOperationsQueryRequest;
import org.ysu.ckqaback.qa.dto.QaSourceReviewResponse;
import org.ysu.ckqaback.qa.ops.QaOperationLogRow;
import org.ysu.ckqaback.qa.ops.QaOperationSourceRow;
import org.ysu.ckqaback.service.QaSourceReviewsService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 管理端问答运维聚合服务。
 */
@Service
@RequiredArgsConstructor
public class QaOperationsService {

    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long EXPORT_LIMIT = 1000;

    private final QaOperationsMapper operationsMapper;
    private final QaSourceReviewsService sourceReviewsService;

    public ApiPageData<QaOperationLogResponse> pageLogs(QaOperationsQueryRequest request, AuthenticatedUser currentUser) {
        Scope scope = requireOpsScope(currentUser);
        QaOperationsQueryRequest effective = withDefaults(request, false);
        long page = Math.max(1, effective.getPage());
        long size = Math.min(Math.max(1, effective.getSize()), 100);
        long total = operationsMapper.countLogs(effective, currentUser.id(), scope.adminScope());
        List<QaOperationLogResponse> items = operationsMapper.selectLogs(
                        effective,
                        currentUser.id(),
                        scope.adminScope(),
                        (page - 1) * size,
                        size
                )
                .stream()
                .map(QaOperationLogResponse::fromRow)
                .toList();
        long pages = size > 0 ? (long) Math.ceil((double) total / (double) size) : 0;
        return new ApiPageData<>(items, page, size, total, pages);
    }

    public QaOperationLogDetailResponse getLogDetail(Long retrievalLogId, AuthenticatedUser currentUser) {
        Scope scope = requireOpsScope(currentUser);
        QaOperationLogRow row = operationsMapper.selectLogDetail(retrievalLogId, currentUser.id(), scope.adminScope());
        if (row == null) {
            throw new BusinessException(ApiResultCode.BAD_REQUEST, HttpStatus.NOT_FOUND, "问答任务不存在或无权访问");
        }
        List<QaOperationFeedbackResponse> feedback = operationsMapper.selectFeedbackByLogIds(List.of(retrievalLogId))
                .stream()
                .map(QaOperationFeedbackResponse::fromRow)
                .toList();
        List<QaOperationSourceRow> sourceRows = operationsMapper.selectSourcesByLogId(retrievalLogId);
        Map<Long, List<QaSourceReviewResponse>> reviewsByHitId = sourceReviewsService.findReviewsByHitIds(
                sourceRows.stream().map(QaOperationSourceRow::getId).toList()
        );
        List<QaOperationSourceResponse> sources = sourceRows.stream()
                .map(source -> QaOperationSourceResponse.fromRow(
                        source,
                        reviewsByHitId.getOrDefault(source.getId(), List.of())
                ))
                .toList();
        return QaOperationLogDetailResponse.of(row, feedback, sources);
    }

    public List<QaOperationLogDetailResponse> exportLogs(QaOperationsQueryRequest request, AuthenticatedUser currentUser) {
        Scope scope = requireOpsScope(currentUser);
        QaOperationsQueryRequest effective = withDefaults(request, true);
        return operationsMapper.selectLogs(effective, currentUser.id(), scope.adminScope(), 0, EXPORT_LIMIT)
                .stream()
                .map(row -> getLogDetail(row.getRetrievalLogId(), currentUser))
                .toList();
    }

    private QaOperationsQueryRequest withDefaults(QaOperationsQueryRequest request, boolean export) {
        QaOperationsQueryRequest effective = request == null ? new QaOperationsQueryRequest() : request;
        if (!StringUtils.hasText(effective.getCreatedFrom()) && !StringUtils.hasText(effective.getCreatedTo())) {
            effective.setCreatedFrom(LocalDateTime.now(SHANGHAI_ZONE).minusDays(7).format(MYSQL_DATETIME));
        }
        if (export) {
            effective.setPage(1);
            effective.setSize(EXPORT_LIMIT);
        }
        return effective;
    }

    private Scope requireOpsScope(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        if (hasRole(currentUser, "admin") || hasPermission(currentUser, "*")) {
            return new Scope(true);
        }
        if (hasRole(currentUser, "teacher") || hasRole(currentUser, "assistant")
                || hasPermission(currentUser, "qa:log:read")) {
            return new Scope(false);
        }
        throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "无问答运维权限");
    }

    private boolean hasRole(AuthenticatedUser user, String role) {
        return user.roles() != null && user.roles().stream().anyMatch(role::equalsIgnoreCase);
    }

    private boolean hasPermission(AuthenticatedUser user, String permission) {
        return user.permissions() != null && user.permissions().contains(permission);
    }

    private record Scope(boolean adminScope) {
    }
}
