package org.ysu.ckqaback.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.AuthContext;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.memory.QaMemoryService;
import org.ysu.ckqaback.qa.memory.dto.QaMemoryItemResponse;
import org.ysu.ckqaback.qa.memory.dto.QaMemoryPreferenceResponse;
import org.ysu.ckqaback.qa.memory.dto.UpdateQaMemoryPreferenceRequest;

import java.util.List;

/**
 * 学生端长期记忆 API。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.QA_MEMORY)
public class QaMemoryController {

    private final QaMemoryService qaMemoryService;

    @GetMapping("/preferences")
    public ApiResponse<QaMemoryPreferenceResponse> getPreferences(
            @RequestParam @NotBlank(message = "courseId不能为空") String courseId,
            @RequestParam @Positive(message = "knowledgeBaseId必须大于0") Long knowledgeBaseId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaMemoryService.getPreferences(courseId, knowledgeBaseId, currentUser(servletRequest)));
    }

    @PutMapping("/preferences")
    public ApiResponse<QaMemoryPreferenceResponse> updatePreferences(
            @Valid @RequestBody UpdateQaMemoryPreferenceRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaMemoryService.updatePreferences(request, currentUser(servletRequest)));
    }

    @GetMapping("/items")
    public ApiResponse<List<QaMemoryItemResponse>> listItems(
            @RequestParam @NotBlank(message = "courseId不能为空") String courseId,
            @RequestParam @Positive(message = "knowledgeBaseId必须大于0") Long knowledgeBaseId,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaMemoryService.listItems(courseId, knowledgeBaseId, currentUser(servletRequest)));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> deleteItem(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            HttpServletRequest servletRequest
    ) {
        qaMemoryService.deleteItem(id, currentUser(servletRequest));
        return ApiResponseUtils.success(null);
    }

    private AuthenticatedUser currentUser(HttpServletRequest servletRequest) {
        AuthenticatedUser currentUser = AuthContext.fromRequestOrCurrentJwt(servletRequest);
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        return currentUser;
    }
}
