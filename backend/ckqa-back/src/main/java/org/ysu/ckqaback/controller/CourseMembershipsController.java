package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.auth.AuthContext;
import org.ysu.ckqaback.course.CourseAccessDecision;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.CourseMembershipManagementService;
import org.ysu.ckqaback.course.dto.CourseMembershipCreateRequest;
import org.ysu.ckqaback.course.dto.CourseMembershipQueryRequest;
import org.ysu.ckqaback.course.dto.CourseMembershipResponse;
import org.ysu.ckqaback.course.dto.CourseMembershipUpdateRequest;

/**
 * <p>
 * 课程成员关系表 前端控制器
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.COURSE_MEMBERSHIPS)
public class CourseMembershipsController {

    private final CourseMembershipManagementService managementService;
    private final CourseAccessService accessService;

    @GetMapping
    public ApiResponse<ApiPageData<CourseMembershipResponse>> listCourseMembers(
            @Valid CourseMembershipQueryRequest request,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        return ApiResponseUtils.success(managementService.listCourseMembers(
                request.getCourseId(),
                request,
                AuthContext.resolveUserCode(actorUserCode)
        ));
    }

    @PostMapping
    public ApiResponse<CourseMembershipResponse> createCourseMember(
            @Valid @RequestBody CourseMembershipCreateRequest request,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        return ApiResponseUtils.success(managementService.createCourseMember(
                request.getCourseId(),
                request,
                AuthContext.resolveUserCode(actorUserCode)
        ));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CourseMembershipResponse> updateCourseMember(
            @PathVariable Long id,
            @Valid @RequestBody CourseMembershipUpdateRequest request,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        return ApiResponseUtils.success(managementService.updateCourseMember(
                request.getCourseId(),
                id,
                request,
                AuthContext.resolveUserCode(actorUserCode)
        ));
    }

    @GetMapping("/access")
    public ApiResponse<CourseAccessDecision> resolveCourseAccess(
            @RequestParam String courseId,
            @RequestParam(required = false) String userCode,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        String currentUserCode = AuthContext.resolveUserCode(actorUserCode);
        String targetUserCode = userCode == null || userCode.isBlank() ? currentUserCode : userCode;
        return ApiResponseUtils.success(accessService.resolveAccess(courseId, targetUserCode));
    }
}
