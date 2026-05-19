package org.ysu.ckqaback.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.auth.AuthContext;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.qa.QaOperationsService;
import org.ysu.ckqaback.qa.dto.QaOperationLogDetailResponse;
import org.ysu.ckqaback.qa.dto.QaOperationLogResponse;
import org.ysu.ckqaback.qa.dto.QaOperationsQueryRequest;
import org.ysu.ckqaback.qa.dto.QaSourceReviewResponse;
import org.ysu.ckqaback.qa.dto.UpsertQaSourceReviewRequest;
import org.ysu.ckqaback.service.QaSourceReviewsService;

import java.util.List;

/**
 * 管理端问答运维接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.QA_OPERATIONS)
public class QaOperationsController {

    private final QaOperationsService qaOperationsService;
    private final QaSourceReviewsService sourceReviewsService;

    @GetMapping("/logs")
    public ApiResponse<ApiPageData<QaOperationLogResponse>> listLogs(
            @Valid @ModelAttribute QaOperationsQueryRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaOperationsService.pageLogs(request, currentUser(servletRequest)));
    }

    @GetMapping("/logs/export")
    public ApiResponse<List<QaOperationLogDetailResponse>> exportLogs(
            @Valid @ModelAttribute QaOperationsQueryRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaOperationsService.exportLogs(request, currentUser(servletRequest)));
    }

    @GetMapping("/logs/{retrievalLogId}")
    public ApiResponse<QaOperationLogDetailResponse> getLogDetail(
            @PathVariable @Positive(message = "retrievalLogId必须大于0") Long retrievalLogId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaOperationsService.getLogDetail(retrievalLogId, currentUser(servletRequest)));
    }

    @PutMapping("/source-reviews/{retrievalHitId}")
    public ApiResponse<QaSourceReviewResponse> upsertSourceReview(
            @PathVariable @Positive(message = "retrievalHitId必须大于0") Long retrievalHitId,
            @Valid @RequestBody UpsertQaSourceReviewRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(sourceReviewsService.upsertReview(retrievalHitId, request, currentUser(servletRequest)));
    }

    private AuthenticatedUser currentUser(HttpServletRequest servletRequest) {
        return AuthContext.fromRequestOrCurrentJwt(servletRequest);
    }
}
