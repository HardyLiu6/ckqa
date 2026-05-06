package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.auth.AuthContext;
import org.ysu.ckqaback.course.CourseAccessService;
import org.ysu.ckqaback.course.CourseMaterialManagementService;
import org.ysu.ckqaback.course.dto.CourseMaterialQueryRequest;
import org.ysu.ckqaback.course.dto.CourseMaterialResponse;
import org.ysu.ckqaback.course.dto.CourseMaterialUpdateRequest;

/**
 * 课程资料正式管理接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.COURSES + "/{courseId}/materials")
public class CourseMaterialsController {

    private final CourseMaterialManagementService materialManagementService;

    @GetMapping
    public ApiResponse<ApiPageData<CourseMaterialResponse>> listMaterials(
            @PathVariable String courseId,
            @Valid @ModelAttribute CourseMaterialQueryRequest request,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        return ApiResponseUtils.success(materialManagementService.listMaterials(
                courseId,
                request,
                AuthContext.resolveUserCode(actorUserCode)
        ));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CourseMaterialResponse> uploadMaterial(
            @PathVariable String courseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "materialType", required = false) String materialType,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        return ApiResponseUtils.success(materialManagementService.uploadMaterial(
                courseId,
                file,
                displayName,
                materialType,
                AuthContext.resolveUserCode(actorUserCode)
        ));
    }

    @GetMapping("/{materialId}")
    public ApiResponse<CourseMaterialResponse> getMaterial(
            @PathVariable String courseId,
            @PathVariable @Positive(message = "materialId必须大于0") Long materialId,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        return ApiResponseUtils.success(materialManagementService.getMaterial(
                courseId,
                materialId,
                AuthContext.resolveUserCode(actorUserCode)
        ));
    }

    @PatchMapping("/{materialId}")
    public ApiResponse<CourseMaterialResponse> updateMaterial(
            @PathVariable String courseId,
            @PathVariable @Positive(message = "materialId必须大于0") Long materialId,
            @Valid @RequestBody CourseMaterialUpdateRequest request,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        return ApiResponseUtils.success(materialManagementService.updateMaterial(
                courseId,
                materialId,
                request,
                AuthContext.resolveUserCode(actorUserCode)
        ));
    }

    @DeleteMapping("/{materialId}")
    public ApiResponse<Void> deleteMaterial(
            @PathVariable String courseId,
            @PathVariable @Positive(message = "materialId必须大于0") Long materialId,
            @RequestHeader(value = CourseAccessService.ACTOR_USER_CODE_HEADER, required = false) String actorUserCode
    ) {
        materialManagementService.deleteMaterial(courseId, materialId, AuthContext.resolveUserCode(actorUserCode));
        return ApiResponseUtils.success();
    }
}
