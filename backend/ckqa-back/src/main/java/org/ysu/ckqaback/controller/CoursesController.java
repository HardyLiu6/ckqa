package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.course.CourseLookupService;
import org.ysu.ckqaback.course.dto.CourseCreateRequest;
import org.ysu.ckqaback.course.dto.CourseDetailResponse;
import org.ysu.ckqaback.course.dto.CoursePdfFileSummaryResponse;
import org.ysu.ckqaback.course.dto.CourseQueryRequest;
import org.ysu.ckqaback.course.dto.CourseSummaryResponse;
import org.ysu.ckqaback.course.dto.KnowledgeBaseSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping
    public ApiResponse<ApiPageData<CourseSummaryResponse>> listCourses(@Valid @ModelAttribute CourseQueryRequest request) {
        return ApiResponseUtils.success(courseLookupService.listCourses(request));
    }

    @PostMapping
    public ApiResponse<CourseDetailResponse> createCourse(@Valid @RequestBody CourseCreateRequest request) {
        return ApiResponseUtils.success(courseLookupService.createCourse(request));
    }

    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailResponse> getCourseDetail(@PathVariable String courseId) {
        return ApiResponseUtils.success(courseLookupService.getCourseDetail(courseId));
    }

    @GetMapping("/{courseId}/pdf-files")
    public ApiResponse<List<CoursePdfFileSummaryResponse>> listCoursePdfFiles(@PathVariable String courseId) {
        return ApiResponseUtils.success(courseLookupService.listCoursePdfFiles(courseId));
    }

    @GetMapping("/{courseId}/knowledge-bases")
    public ApiResponse<List<KnowledgeBaseSummaryResponse>> listKnowledgeBases(@PathVariable String courseId) {
        return ApiResponseUtils.success(courseLookupService.listKnowledgeBases(courseId));
    }
}
