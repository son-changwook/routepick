package com.routepick.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import com.routepick.api.exception.customExceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;

/**
 * 기본 예외 처리 핸들러
 * 예외 발생 시 일괄적으로 처리하고 응답을 반환합니다.
 */
@Slf4j
@ControllerAdvice
public class DefaultExceptionHandler {

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    private ApiError createApiError(HttpServletRequest request, Exception ex, HttpStatus status, String errorCode) {
        String message = ex.getMessage();
        String details = null;

        // 개발 환경에서만 상세 정보 제공
        if ("dev".equals(activeProfile)) {
            details = ex.toString();
        }

        // 보안 관련 예외는 일반화된 메시지 사용
        if (ex instanceof AccessDeniedException || ex instanceof InsufficientAuthenticationException) {
            message = "접근이 거부되었습니다.";
        }

        ApiError apiError = new ApiError(
                request.getRequestURI(),
                message,
                status.value(),
                errorCode,
                LocalDateTime.now(),
                details
        );

        // 로깅
        if (status.is5xxServerError()) {
            log.error("Exception occurred: ", ex);
        } else {
            log.warn("Exception occurred: {}", ex.getMessage());
        }

        return apiError;
    }

    @ExceptionHandler(RequestNotAcceptableException.class)
    public ResponseEntity<ApiError> handleException(
            RequestNotAcceptableException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .body(createApiError(request, ex, HttpStatus.NOT_ACCEPTABLE, "REQUEST_NOT_ACCEPTABLE"));
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ApiError> handleException(
            RequestValidationException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createApiError(request, ex, HttpStatus.BAD_REQUEST, "INVALID_REQUEST"));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleException(
            DuplicateResourceException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(createApiError(request, ex, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleException(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(createApiError(request, ex, HttpStatus.FORBIDDEN, "ACCESS_DENIED"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleException(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(createApiError(request, ex, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ApiError> handleException(
            InsufficientAuthenticationException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(createApiError(request, ex, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleException(
            BadCredentialsException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(createApiError(request, ex, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createApiError(request, ex, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createApiError(request, ex, HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleException(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(createApiError(request, ex, HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleException(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(createApiError(request, ex, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED"));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleException(
            NoHandlerFoundException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(createApiError(request, ex, HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createApiError(request, ex, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR"));
    }
}