package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.qa.QaWorkflowService;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 问答会话表 前端控制器
 * </p>
 *
 * @author codex
 * @since 2026-04-21
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.QA_SESSIONS)
public class QaSessionsController {

    private final QaWorkflowService qaWorkflowService;

    @PostMapping
    public ApiResponse<QaSessionResponse> createSession(@Valid @RequestBody CreateQaSessionRequest request) {
        return ApiResponseUtils.success(qaWorkflowService.createSession(request));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<QaTaskSubmissionResponse> sendMessage(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody CreateQaMessageRequest request
    ) {
        return ApiResponseUtils.success(qaWorkflowService.sendMessage(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<QaSessionResponse> getSession(@PathVariable @Positive(message = "id必须大于0") Long id) {
        return ApiResponseUtils.success(qaWorkflowService.getSession(id));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<QaMessageResponse>> listMessages(@PathVariable @Positive(message = "id必须大于0") Long id) {
        return ApiResponseUtils.success(qaWorkflowService.listMessages(id));
    }

    @GetMapping("/{sessionId}/tasks/{taskId}")
    public ApiResponse<QaTaskDetailResponse> getTaskDetail(
            @PathVariable @Positive(message = "sessionId必须大于0") Long sessionId,
            @PathVariable @Positive(message = "taskId必须大于0") Long taskId
    ) {
        return ApiResponseUtils.success(qaWorkflowService.getTaskDetail(sessionId, taskId));
    }
}
