package org.ysu.ckqaback.controller;

import lombok.RequiredArgsConstructor;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.course.CourseLookupService;
import org.ysu.ckqaback.course.dto.CoursePdfFileSummaryResponse;
import org.ysu.ckqaback.course.dto.KnowledgeBaseSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/{courseId}/pdf-files")
    public ApiResponse<List<CoursePdfFileSummaryResponse>> listCoursePdfFiles(@PathVariable String courseId) {
        return ApiResponseUtils.success(courseLookupService.listCoursePdfFiles(courseId));
    }

    @GetMapping("/{courseId}/knowledge-bases")
    public ApiResponse<List<KnowledgeBaseSummaryResponse>> listKnowledgeBases(@PathVariable String courseId) {
        return ApiResponseUtils.success(courseLookupService.listKnowledgeBases(courseId));
    }
}
