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
import org.ysu.ckqaback.course.dto.CourseRoutingRecommendRequest;
import org.ysu.ckqaback.course.dto.CourseRoutingRecommendResponse;
import org.ysu.ckqaback.course.routing.CourseRoutingService;
import org.ysu.ckqaback.exception.BusinessException;

/**
 * 学生端无显式课程时的课程自动路由。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.COURSE_ROUTING)
public class CourseRoutingController {

    private final CourseRoutingService courseRoutingService;

    @PostMapping("/recommend")
    public ApiResponse<CourseRoutingRecommendResponse> recommend(
            @Valid @RequestBody CourseRoutingRecommendRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(courseRoutingService.recommend(request, currentUser(servletRequest)));
    }

    private AuthenticatedUser currentUser(HttpServletRequest servletRequest) {
        AuthenticatedUser currentUser = AuthContext.fromRequestOrCurrentJwt(servletRequest);
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        return currentUser;
    }
}
