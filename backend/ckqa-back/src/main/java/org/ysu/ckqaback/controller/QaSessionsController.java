package org.ysu.ckqaback.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.ysu.ckqaback.api.ApiPageData;
import org.ysu.ckqaback.api.ApiPaths;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;
import org.ysu.ckqaback.auth.AuthContext;
import org.ysu.ckqaback.auth.AuthenticatedUser;
import org.ysu.ckqaback.exception.BusinessException;
import org.ysu.ckqaback.qa.QaWorkflowService;
import org.ysu.ckqaback.qa.dto.CreateQaMessageRequest;
import org.ysu.ckqaback.qa.dto.CreateQaSessionRequest;
import org.ysu.ckqaback.qa.dto.ForkQaSessionRequest;
import org.ysu.ckqaback.qa.dto.QaHybridWarmupRequest;
import org.ysu.ckqaback.qa.dto.QaHybridWarmupResponse;
import org.ysu.ckqaback.qa.dto.QaMessageResponse;
import org.ysu.ckqaback.qa.dto.QaSessionForkResponse;
import org.ysu.ckqaback.qa.dto.QaSessionQueryRequest;
import org.ysu.ckqaback.qa.dto.QaSessionResponse;
import org.ysu.ckqaback.qa.dto.QaSessionStatsResponse;
import org.ysu.ckqaback.qa.dto.QaTaskDetailResponse;
import org.ysu.ckqaback.qa.dto.QaTaskSubmissionResponse;
import org.ysu.ckqaback.qa.dto.QaTranscriptResponse;
import org.ysu.ckqaback.qa.dto.UpdateQaSessionRequest;
import org.ysu.ckqaback.qa.stream.QaTaskEventStreamService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
    private final QaTaskEventStreamService qaTaskEventStreamService;

    @PostMapping
    public ApiResponse<QaSessionResponse> createSession(
            @Valid @RequestBody CreateQaSessionRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser currentUser = AuthContext.fromRequestOrCurrentJwt(servletRequest);
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        if (request.getUserId() != null && !currentUser.id().equals(request.getUserId())) {
            throw new BusinessException(ApiResultCode.AUTH_FORBIDDEN, HttpStatus.FORBIDDEN, "只能为当前登录用户创建问答会话");
        }
        return ApiResponseUtils.success(qaWorkflowService.createSession(request, currentUser));
    }

    @PostMapping("/hybrid-warmup")
    public ApiResponse<QaHybridWarmupResponse> warmupHybrid(
            @Valid @RequestBody QaHybridWarmupRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaWorkflowService.warmupHybrid(request, currentUser(servletRequest)));
    }

    @GetMapping
    public ApiResponse<ApiPageData<QaSessionResponse>> listSessions(
            @Valid @ModelAttribute QaSessionQueryRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser currentUser = AuthContext.fromRequestOrCurrentJwt(servletRequest);
        return ApiResponseUtils.success(qaWorkflowService.listSessions(currentUser == null ? null : currentUser.id(), request));
    }

    @GetMapping("/stats")
    public ApiResponse<QaSessionStatsResponse> sessionStats(
            @Valid @ModelAttribute QaSessionQueryRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser currentUser = AuthContext.fromRequestOrCurrentJwt(servletRequest);
        return ApiResponseUtils.success(qaWorkflowService.statsSessions(currentUser == null ? null : currentUser.id(), request));
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<QaTaskSubmissionResponse> sendMessage(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody CreateQaMessageRequest request,
            HttpServletRequest servletRequest
    ) {
        AuthenticatedUser currentUser = currentUser(servletRequest);
        qaWorkflowService.ensureSessionOwner(id, currentUser.id());
        return ApiResponseUtils.success(qaWorkflowService.sendMessage(id, request, currentUser));
    }

    @GetMapping("/{id}")
    public ApiResponse<QaSessionResponse> getSession(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            HttpServletRequest servletRequest
    ) {
        qaWorkflowService.ensureSessionOwner(id, currentUserId(servletRequest));
        return ApiResponseUtils.success(qaWorkflowService.getSession(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<QaSessionResponse> updateSession(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody UpdateQaSessionRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaWorkflowService.updateSession(id, request, currentUser(servletRequest)));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<QaMessageResponse>> listMessages(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            HttpServletRequest servletRequest
    ) {
        qaWorkflowService.ensureSessionOwner(id, currentUserId(servletRequest));
        return ApiResponseUtils.success(qaWorkflowService.listMessages(id, currentUserId(servletRequest)));
    }

    @GetMapping("/{id}/transcript")
    public ApiResponse<QaTranscriptResponse> getTranscript(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaWorkflowService.getTranscript(id, currentUserId(servletRequest)));
    }

    @PostMapping("/{id}/fork")
    public ApiResponse<QaSessionForkResponse> forkSession(
            @PathVariable @Positive(message = "id必须大于0") Long id,
            @Valid @RequestBody(required = false) ForkQaSessionRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponseUtils.success(qaWorkflowService.forkSession(id, request, currentUser(servletRequest)));
    }

    @GetMapping("/{sessionId}/tasks/{taskId}")
    public ApiResponse<QaTaskDetailResponse> getTaskDetail(
            @PathVariable @Positive(message = "sessionId必须大于0") Long sessionId,
            @PathVariable @Positive(message = "taskId必须大于0") Long taskId,
            HttpServletRequest servletRequest
    ) {
        qaWorkflowService.ensureSessionOwner(sessionId, currentUserId(servletRequest));
        return ApiResponseUtils.success(qaWorkflowService.getTaskDetail(sessionId, taskId, currentUserId(servletRequest)));
    }

    @GetMapping(value = "/{sessionId}/tasks/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskEvents(
            @PathVariable @Positive(message = "sessionId必须大于0") Long sessionId,
            @PathVariable @Positive(message = "taskId必须大于0") Long taskId,
            @RequestParam(required = false, defaultValue = "0") Long afterEventSeq,
            HttpServletRequest servletRequest
    ) {
        Long currentUserId = currentUserId(servletRequest);
        qaWorkflowService.ensureSessionOwner(sessionId, currentUserId);
        return qaTaskEventStreamService.openStream(sessionId, taskId, currentUserId, afterEventSeq);
    }

    private Long currentUserId(HttpServletRequest servletRequest) {
        return currentUser(servletRequest).id();
    }

    private AuthenticatedUser currentUser(HttpServletRequest servletRequest) {
        AuthenticatedUser currentUser = AuthContext.fromRequestOrCurrentJwt(servletRequest);
        if (currentUser == null || currentUser.id() == null) {
            throw new BusinessException(ApiResultCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED);
        }
        return currentUser;
    }
}
