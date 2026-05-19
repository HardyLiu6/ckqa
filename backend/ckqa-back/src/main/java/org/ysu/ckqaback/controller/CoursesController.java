package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.auth.AuthContext;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.CourseCommandService;
import org.ysu.ckqaback.course.CourseLookupService;
import org.ysu.ckqaback.course.dto.CourseCreateRequest;
import org.ysu.ckqaback.course.dto.CourseCoverUploadResponse;
import org.ysu.ckqaback.course.dto.CourseChaptersResponse;
import org.ysu.ckqaback.course.dto.CourseDetailResponse;
import org.ysu.ckqaback.course.dto.CoursePdfFileSummaryResponse;
import org.ysu.ckqaback.course.dto.CourseProgressResponse;
import org.ysu.ckqaback.course.dto.CourseQueryRequest;
import org.ysu.ckqaback.course.dto.CourseSummaryResponse;
import org.ysu.ckqaback.course.dto.KnowledgeBaseSummaryResponse;
import org.ysu.ckqaback.course.dto.CourseUpdateRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * <p>
 * 课程表 前端控制器
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.COURSES)
public class CoursesController {

    private final CourseLookupService courseLookupService;
    private final CourseCommandService courseCommandService;

    @GetMapping
    public ApiResponse<ApiPageData<CourseSummaryResponse>> listCourses(
            @Valid @ModelAttribute CourseQueryRequest request,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        String resolvedUserCode = AuthContext.resolveUserCode(actorUserCode);
        if (resolvedUserCode == null || resolvedUserCode.isBlank()) {
            return ApiResponseUtils.success(courseLookupService.listCourses(request));
        }
        return ApiResponseUtils.success(courseLookupService.listCourses(request, resolvedUserCode));
    }

    @PostMapping
    public ApiResponse<CourseDetailResponse> createCourse(@Valid @RequestBody CourseCreateRequest request) {
        return ApiResponseUtils.success(courseCommandService.createCourse(request));
    }

    @PutMapping("/{courseId}")
    public ApiResponse<CourseDetailResponse> updateCourse(
            @PathVariable String courseId,
            @Valid @RequestBody CourseUpdateRequest request
    ) {
        courseCommandService.updateCourse(courseId, request);
        return ApiResponseUtils.success(courseLookupService.getCourseDetail(courseId));
    }

    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> deleteCourse(@PathVariable String courseId) {
        courseCommandService.deleteCourse(courseId);
        return ApiResponseUtils.success();
    }

    @PostMapping(value = "/covers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CourseCoverUploadResponse> uploadCourseCover(@RequestParam("file") MultipartFile file) {
        return ApiResponseUtils.success(courseCommandService.storeCourseCover(file));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailResponse> getCourseDetail(
            @PathVariable String courseId,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        String resolvedUserCode = AuthContext.resolveUserCode(actorUserCode);
        if (resolvedUserCode == null || resolvedUserCode.isBlank()) {
            return ApiResponseUtils.success(courseLookupService.getCourseDetail(courseId));
        }
        return ApiResponseUtils.success(courseLookupService.getCourseDetail(courseId, resolvedUserCode));
    }

    @PostMapping(value = "/{courseId}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CourseCoverUploadResponse> updateCourseCover(
            @PathVariable String courseId,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponseUtils.success(courseCommandService.updateCourseCover(courseId, file));
    }

    @GetMapping("/{courseId}/pdf-files")
    public ApiResponse<List<CoursePdfFileSummaryResponse>> listCoursePdfFiles(@PathVariable String courseId) {
        return ApiResponseUtils.success(courseLookupService.listCoursePdfFiles(courseId));
    }

    @GetMapping("/{courseId}/knowledge-bases")
    public ApiResponse<List<KnowledgeBaseSummaryResponse>> listKnowledgeBases(
            @PathVariable String courseId,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        String resolvedUserCode = AuthContext.resolveUserCode(actorUserCode);
        return ApiResponseUtils.success(courseLookupService.listKnowledgeBases(courseId, resolvedUserCode));
    }

    /**
     * 课程章节列表（占位接口）。
     * <p>完整 LMS 章节 / 课时尚未上线，当前仅返回结构化 {@code coming-soon} 响应供前端展示占位卡。</p>
     */
    @GetMapping("/{courseId}/chapters")
    public ApiResponse<CourseChaptersResponse> listChapters(@PathVariable String courseId) {
        courseLookupService.getCourseDetail(courseId);
        return ApiResponseUtils.success(CourseChaptersResponse.comingSoon());
    }

    /**
     * 当前学生在该课程下的学习进度（占位接口）。
     * <p>{@code enrolled} 字段实时反映成员关系，便于前端按"已加入但功能未开放"和"未加入"分别降级。</p>
     */
    @GetMapping("/{courseId}/progress/me")
    public ApiResponse<CourseProgressResponse> getMyProgress(
            @PathVariable String courseId,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        String resolvedUserCode = AuthContext.resolveUserCode(actorUserCode);
        boolean enrolled = courseLookupService.isUserMemberOfCourse(resolvedUserCode, courseId);
        return ApiResponseUtils.success(CourseProgressResponse.comingSoon(enrolled));
    }
}
