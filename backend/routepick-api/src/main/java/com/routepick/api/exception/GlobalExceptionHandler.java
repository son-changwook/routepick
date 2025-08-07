package com.routepick.api.exception;

import com.routepick.common.dto.ApiResponse;
import com.routepick.common.exception.BaseException;
import com.routepick.common.exception.BusinessException;
import com.routepick.common.exception.ValidationException;
import com.routepick.common.exception.customExceptions.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리기
 * 모든 예외를 일관된 형식으로 처리합니다.
 * JDK 기본 예외를 커스텀 예외로 변환하여 처리합니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Base Exception 처리
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException e, HttpServletRequest request) {
        logException(e, request, "Business exception occurred");
        
        ApiResponse<Void> response = ApiResponse.error(
            e.getHttpStatus().value(),
            e.getUserMessage()
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
            e.getUserMessage()
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
            e.getMessage()
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
            e.getMessage()
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
            e.getMessage()
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
            e.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(response);
    }

    /**
     * Spring Validation (@Valid) 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        logException(e, request, "Method argument validation failed");
        
        String fieldName = e.getBindingResult().getFieldError() != null ? 
            e.getBindingResult().getFieldError().getField() : "unknown";
        String message = e.getBindingResult().getFieldError() != null ? 
            e.getBindingResult().getFieldError().getDefaultMessage() : "Validation failed";
        
        // 커스텀 ValidationException으로 변환
        ValidationException customException = new ValidationException(fieldName, message);
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * Bean Validation (@Validated) 처리
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        logException(e, request, "Constraint violation exception occurred");
        
        String violations = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        
        // 커스텀 ValidationException으로 변환
        ValidationException customException = new ValidationException("validation", violations);
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * Binding Exception 처리
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e, HttpServletRequest request) {
        logException(e, request, "Binding exception occurred");
        
        String fieldName = e.getBindingResult().getFieldError() != null ? 
            e.getBindingResult().getFieldError().getField() : "unknown";
        String message = e.getBindingResult().getFieldError() != null ? 
            e.getBindingResult().getFieldError().getDefaultMessage() : "Binding failed";
        
        // 커스텀 ValidationException으로 변환
        ValidationException customException = new ValidationException(fieldName, message);
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * HTTP Method Not Supported 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logException(e, request, "HTTP method not supported");
        
        // 커스텀 MethodNotAllowedException으로 변환
        MethodNotAllowedException customException = new MethodNotAllowedException(
            e.getMethod(), 
            e.getSupportedMethods() != null ? String.join(", ", e.getSupportedMethods()) : ""
        );
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * Missing Servlet Request Parameter 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
        logException(e, request, "Missing servlet request parameter");
        
        // 커스텀 MissingParameterException으로 변환
        MissingParameterException customException = new MissingParameterException(
            e.getParameterName(), 
            e.getParameterType()
        );
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * Method Argument Type Mismatch 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        logException(e, request, "Method argument type mismatch");
        
        // 커스텀 TypeMismatchException으로 변환
        TypeMismatchException customException = new TypeMismatchException(
            e.getName(), 
            e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : "unknown"
        );
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * HTTP Message Not Readable 처리
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        logException(e, request, "HTTP message not readable");
        
        // 커스텀 InvalidJsonException으로 변환
        InvalidJsonException customException = new InvalidJsonException(e.getMessage());
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * No Handler Found 처리
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        logException(e, request, "No handler found");
        
        // 커스텀 EndpointNotFoundException으로 변환
        EndpointNotFoundException customException = new EndpointNotFoundException(
            e.getHttpMethod(), 
            e.getRequestURL()
        );
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * Access Denied 처리
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException e, HttpServletRequest request) {
        logException(e, request, "Access denied");
        
        // 커스텀 AccessDeniedException으로 변환
        com.routepick.common.exception.customExceptions.AccessDeniedException customException = 
            new com.routepick.common.exception.customExceptions.AccessDeniedException(
                request.getRequestURI(), 
                e.getMessage()
            );
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * Runtime Exception 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        logException(e, request, "Runtime exception occurred", true);
        
        // 커스텀 InternalServerException으로 변환
        InternalServerException customException = new InternalServerException(e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * General Exception 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        logException(e, request, "Unexpected exception occurred", true);
        
        // 커스텀 InternalServerException으로 변환
        InternalServerException customException = new InternalServerException(e.getMessage(), e);
        
        ApiResponse<Void> response = ApiResponse.error(
            customException.getHttpStatus().value(),
            customException.getUserMessage()
        );
        
        return ResponseEntity.status(customException.getHttpStatus()).body(response);
    }

    /**
     * 예외 로깅
     */
    private void logException(Exception e, HttpServletRequest request, String message) {
        logException(e, request, message, false);
    }

    /**
     * 예외 로깅 (에러 레벨 지정)
     */
    private void logException(Exception e, HttpServletRequest request, String message, boolean isError) {
        String logMessage = String.format("%s - %s %s - %s", 
            message, request.getMethod(), request.getRequestURI(), e.getMessage());
        
        if (isError) {
            log.error(logMessage, e);
        } else {
            log.warn(logMessage, e);
        }
    }
}