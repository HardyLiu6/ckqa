package org.ysu.ckqaback.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthContext;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.dto.QaModeRecommendationRequest;
import org.ysu.ckqaback.qa.dto.QaModeRecommendationResponse;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckRequest;
import org.ysu.ckqaback.qa.dto.QaQuestionDomainCheckResponse;
import org.ysu.ckqaback.qa.routing.QaModeRoutingService;
import org.ysu.ckqaback.qa.routing.QaQuestionDomainGuardService;

/**
 * 学生端智能问答模式推荐。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.QA_ROUTING)
public class QaRoutingController {

    private final QaModeRoutingService qaModeRoutingService;
    private final QaQuestionDomainGuardService qaQuestionDomainGuardService;

    @PostMapping("/recommend")
    public ApiResponse<QaModeRecommendationResponse> recommend(
            @Valid @RequestBody QaModeRecommendationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaModeRoutingService.recommend(request, currentUser(servletRequest)));
    }

    @PostMapping("/domain-check")
    public ApiResponse<QaQuestionDomainCheckResponse> checkDomain(
            @Valid @RequestBody QaQuestionDomainCheckRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaQuestionDomainGuardService.check(request, currentUser(servletRequest)));
    }

    private AuthenticatedUser currentUser(HttpServletRequest servletRequest) {
        AuthenticatedUser currentUser = AuthContext.fromRequestOrCurrentJwt(servletRequest);
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        return currentUser;
    }
}
