package com.routepick.api.exception;

import com.routepick.api.util.SecureLogger;
import com.routepick.common.dto.ApiResponse;
import com.routepick.common.exception.BaseException;

import com.routepick.common.exception.ValidationException;
import com.routepick.common.exception.customExceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.nio.file.AccessDeniedException;
import java.util.stream.Collectors;

/**
 * 글로벌 예외 처리기
 * 모든 예외를 일관된 ApiResponse 형태로 처리합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 커스텀 Base Exception 처리
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException e, HttpServletRequest request) {
        logException(e, request, "Business exception occurred");
        
        ApiResponse<Void> response = ApiResponse.error(
            e.getHttpStatus().value(),
            e.getUserMessage(),
            ApiResponse.ErrorDetail.of(e.getErrorCode(), e.getDeveloperMessage())
        );
        
        return ResponseEntity.status(e.getHttpStatus()).body(response);
    }

    /**
     * Validation Exception 처리 (커스텀)
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(ValidationException e, HttpServletRequest request) {
        logException(e, request, "Validation exception occurred");
        
        ApiResponse<Void> response = ApiResponse.error(
            e.getHttpStatus().value(),
            e.getUserMessage(),
            ApiResponse.ErrorDetail.of(e.getFieldName(), e.getUserMessage(), e.getErrorCode())
        );
        
        return ResponseEntity.status(e.getHttpStatus()).body(response);
    }

    /**
     * 기존 DuplicateResourceException 처리
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResourceException(DuplicateResourceException e, HttpServletRequest request) {
        logException(e, request, "Duplicate resource exception occurred");
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.CONFLICT.value(),
            e.getMessage(),
            ApiResponse.ErrorDetail.of("DUPLICATE_RESOURCE", e.getMessage())
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * 기존 ResourceNotFoundException 처리
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException e, HttpServletRequest request) {
        logException(e, request, "Resource not found exception occurred");
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.NOT_FOUND.value(),
            e.getMessage(),
            ApiResponse.ErrorDetail.of("RESOURCE_NOT_FOUND", e.getMessage())
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 기존 RequestValidationException 처리
     */
    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestValidationException(RequestValidationException e, HttpServletRequest request) {
        logException(e, request, "Request validation exception occurred");
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            e.getMessage(),
            ApiResponse.ErrorDetail.of("VALIDATION_ERROR", e.getMessage())
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * 기존 RequestNotAcceptableException 처리
     */
    @ExceptionHandler(RequestNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleRequestNotAcceptableException(RequestNotAcceptableException e, HttpServletRequest request) {
        logException(e, request, "Request not acceptable exception occurred");
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.NOT_ACCEPTABLE.value(),
            e.getMessage(),
            ApiResponse.ErrorDetail.of("REQUEST_NOT_ACCEPTABLE", e.getMessage())
        );
        
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(response);
    }

    /**
     * Spring Validation (@Valid) 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        logException(e, request, "Method argument validation failed");
        
        FieldError fieldError = e.getBindingResult().getFieldError();
        String fieldName = fieldError != null ? fieldError.getField() : "unknown";
        String message = fieldError != null ? fieldError.getDefaultMessage() : "입력값이 올바르지 않습니다.";
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            "입력값 검증에 실패했습니다.",
            ApiResponse.ErrorDetail.of(fieldName, message, "VALIDATION_FAILED")
        );
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * Bean Validation 처리
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        logException(e, request, "Constraint violation occurred");
        
        String violations = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            "입력값 제약 조건 위반",
            ApiResponse.ErrorDetail.of(violations)
        );
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 바인딩 예외 처리
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e, HttpServletRequest request) {
        logException(e, request, "Binding exception occurred");
        
        FieldError fieldError = e.getFieldError();
        String fieldName = fieldError != null ? fieldError.getField() : "unknown";
        String message = fieldError != null ? fieldError.getDefaultMessage() : "바인딩 오류가 발생했습니다.";
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            "입력값 바인딩에 실패했습니다.",
            ApiResponse.ErrorDetail.of(fieldName, message, "BINDING_FAILED")
        );
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * HTTP 메서드 오류 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logException(e, request, "HTTP method not supported");
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            "지원하지 않는 HTTP 메서드입니다.",
            ApiResponse.ErrorDetail.of("METHOD_NOT_ALLOWED", e.getMessage())
        );
        
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    /**
     * 요청 파라미터 누락 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
        logException(e, request, "Missing request parameter");
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            "필수 요청 파라미터가 누락되었습니다.",
            ApiResponse.ErrorDetail.of(e.getParameterName(), "필수 파라미터입니다.", "MISSING_PARAMETER")
        );
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 타입 변환 오류 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        logException(e, request, "Method argument type mismatch");
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            "파라미터 타입이 올바르지 않습니다.",
            ApiResponse.ErrorDetail.of(e.getName(), "올바른 타입으로 입력해주세요.", "TYPE_MISMATCH")
        );
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * JSON 파싱 오류 처리
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        logException(e, request, "HTTP message not readable");
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.BAD_REQUEST.value(),
            "요청 본문의 형식이 올바르지 않습니다.",
            ApiResponse.ErrorDetail.of("JSON_PARSE_ERROR", "올바른 JSON 형식으로 요청해주세요.")
        );
        
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 404 오류 처리
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        logException(e, request, "No handler found");
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.NOT_FOUND.value(),
            "요청한 API를 찾을 수 없습니다.",
            ApiResponse.ErrorDetail.of("ENDPOINT_NOT_FOUND", e.getMessage())
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * 접근 거부 오류 처리
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request) {
        logException(e, request, "Access denied");
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.FORBIDDEN.value(),
            "접근 권한이 없습니다.",
            ApiResponse.ErrorDetail.of("ACCESS_DENIED", e.getMessage())
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * 일반적인 RuntimeException 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        logException(e, request, "Runtime exception occurred", true);
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "서버 내부 오류가 발생했습니다.",
            ApiResponse.ErrorDetail.of("INTERNAL_SERVER_ERROR", "잠시 후 다시 시도해주세요.")
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 모든 예외의 최종 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        logException(e, request, "Unexpected exception occurred", true);
        
        ApiResponse<Void> response = ApiResponse.error(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "예상치 못한 오류가 발생했습니다.",
            ApiResponse.ErrorDetail.of("UNEXPECTED_ERROR", "시스템 관리자에게 문의해주세요.")
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 예외 로깅 헬퍼 메서드
     */
    private void logException(Exception e, HttpServletRequest request, String message) {
        logException(e, request, message, false);
    }

    private void logException(Exception e, HttpServletRequest request, String message, boolean isError) {
        String requestInfo = String.format("method=%s, uri=%s, remoteAddr=%s", 
            request.getMethod(), 
            request.getRequestURI(),
            request.getRemoteAddr());
        
        if (isError) {
            log.error("{}: {} - {}", message, requestInfo, e.getMessage(), e);
            SecureLogger.logSecurityEvent("Exception occurred: {} - {} - {}", message, requestInfo, e.getMessage());
        } else {
            log.warn("{}: {} - {}", message, requestInfo, e.getMessage());
        }
    }
}