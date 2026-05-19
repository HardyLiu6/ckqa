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
import org.ysu.ckqaback.qa.routing.QaModeRoutingService;

/**
 * 学生端智能问答模式推荐。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.QA_ROUTING)
public class QaRoutingController {

    private final QaModeRoutingService qaModeRoutingService;

    @PostMapping("/recommend")
    public ApiResponse<QaModeRecommendationResponse> recommend(
            @Valid @RequestBody QaModeRecommendationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaModeRoutingService.recommend(request, currentUser(servletRequest)));
    }

    private AuthenticatedUser currentUser(HttpServletRequest servletRequest) {
        AuthenticatedUser currentUser = AuthContext.fromRequestOrCurrentJwt(servletRequest);
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        return currentUser;
    }
}
