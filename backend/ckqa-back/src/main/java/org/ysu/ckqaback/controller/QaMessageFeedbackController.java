package org.ysu.ckqaback.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.auth.AuthContext;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.qa.dto.QaFeedbackResponse;
import org.ysu.ckqaback.qa.dto.SubmitQaFeedbackRequest;
import org.ysu.ckqaback.service.QaMessageFeedbackService;

/**
 * 学生端问答反馈接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.QA_MESSAGE_FEEDBACK)
public class QaMessageFeedbackController {

    private final QaMessageFeedbackService feedbackService;

    @PostMapping
    public ApiResponse<QaFeedbackResponse> submitFeedback(
            @Valid @RequestBody SubmitQaFeedbackRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(feedbackService.upsertFeedback(request, currentUser(servletRequest)));
    }

    @DeleteMapping("/{messageId}")
    public ApiResponse<Void> deleteFeedback(
            @PathVariable @Positive(message = "messageId必须大于0") Long messageId,
            HttpServletRequest servletRequest
    ) {
        feedbackService.deleteFeedback(messageId, currentUser(servletRequest));
        return ApiResponseUtils.success(null);
    }

    private AuthenticatedUser currentUser(HttpServletRequest servletRequest) {
        return AuthContext.fromRequestOrCurrentJwt(servletRequest);
    }
}
