package org.ysu.ckqaback.exception;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.ysu.ckqaback.api.ApiErrorDetail;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.api.ApiResponse;
import org.ysu.ckqaback.api.ApiResponseUtils;

import java.util.List;
import java.util.Map;

/**
 * 全局异常处理器。
 * <p>
 * 统一拦截控制器层抛出的常见异常，并转换为标准响应体，
 * 确保接口输出结构稳定且便于前端消费。
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        return json(
                exception.getStatus(),
                ApiResponseUtils.error(exception.getCode(), exception.getMessage(), null)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        return validationErrorResponse(exception.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleBindException(BindException exception) {
        return validationErrorResponse(exception.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        List<ApiErrorDetail> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return json(
                HttpStatus.BAD_REQUEST,
                ApiResponseUtils.result(ApiResultCode.VALIDATION_ERROR, Map.of("errors", errors))
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return json(
                HttpStatus.BAD_REQUEST,
                ApiResponseUtils.error(ApiResultCode.BAD_REQUEST.getCode(), "请求体格式不正确", null)
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return json(
                HttpStatus.BAD_REQUEST,
                ApiResponseUtils.error(ApiResultCode.BAD_REQUEST.getCode(), exception.getMessage(), null)
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception exception, HttpServletResponse response) {
        log.error("未处理的接口异常", exception);
        if (isEventStreamResponse(response)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return json(HttpStatus.INTERNAL_SERVER_ERROR, ApiResponseUtils.error(ApiResultCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> validationErrorResponse(List<FieldError> fieldErrors) {
        List<ApiErrorDetail> errors = fieldErrors.stream()
                .map(error -> new ApiErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return json(
                HttpStatus.BAD_REQUEST,
                ApiResponseUtils.result(ApiResultCode.VALIDATION_ERROR, Map.of("errors", errors))
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> json(HttpStatus status, ApiResponse<T> body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private boolean isEventStreamResponse(HttpServletResponse response) {
        if (response == null) {
            return false;
        }
        String contentType = response.getContentType();
        return contentType != null && contentType.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }
}
