package org.ysu.ckqaback.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponseUtils.error(exception.getCode(), exception.getMessage(), null));
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
        return ResponseEntity.badRequest()
                .body(ApiResponseUtils.result(ApiResultCode.VALIDATION_ERROR, Map.of("errors", errors)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity.badRequest()
                .body(ApiResponseUtils.error(ApiResultCode.BAD_REQUEST.getCode(), "请求体格式不正确", null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponseUtils.error(ApiResultCode.BAD_REQUEST.getCode(), exception.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseUtils.error(ApiResultCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> validationErrorResponse(List<FieldError> fieldErrors) {
        List<ApiErrorDetail> errors = fieldErrors.stream()
                .map(error -> new ApiErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiResponseUtils.result(ApiResultCode.VALIDATION_ERROR, Map.of("errors", errors)));
    }
}
