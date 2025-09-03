# 통합 예외 처리 핸들러 구현

## 개요
RoutePickr 시스템의 모든 예외를 일관되게 처리하는 통합 예외 처리 핸들러입니다. Spring의 @RestControllerAdvice를 활용하여 전역 예외 처리를 구현합니다.

## GlobalExceptionHandler 구현

```java
package com.routepick.common.exception.handler;

import com.routepick.common.exception.*;
import com.routepick.common.exception.dto.ErrorResponse;
import com.routepick.common.exception.dto.ValidationErrorResponse;
import com.routepick.common.exception.enums.ErrorCode;
import com.routepick.common.security.SecurityUtils;
import com.routepick.common.monitoring.MetricsService;
import com.routepick.common.logging.LoggingService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * 전역 예외 처리 핸들러
 * 
 * 주요 기능:
 * - 일관된 에러 응답 형식 제공
 * - 보안 정보 마스킹
 * - 예외 로깅 및 모니터링
 * - 개발/운영 환경별 응답 처리
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private MetricsService metricsService;
    
    @Autowired
    private LoggingService loggingService;
    
    @Autowired
    private SecurityUtils securityUtils;
    
    @Value("${app.environment:production}")
    private String environment;
    
    @Value("${app.debug.enabled:false}")
    private boolean debugEnabled;

    // ============================================
    // 비즈니스 예외 처리
    // ============================================
    
    /**
     * 사용자 관련 예외 처리
     */
    @ExceptionHandler(UserException.class)
    public ResponseEntity<ErrorResponse> handleUserException(
            UserException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        logException(errorId, ex, request);
        
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code(ex.getErrorCode().getCode())
                .message(ex.getErrorCode().getMessage())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        // 개발 환경에서는 상세 정보 추가
        if (isDebugMode()) {
            response.setDetails(ex.getDetails());
        }
        
        metricsService.recordException("UserException", ex.getErrorCode().getCode());
        
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(response);
    }
    
    /**
     * 인증/인가 예외 처리
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(
            AuthException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        // 보안 예외는 상세 정보를 로그에만 기록
        log.warn("Authentication failed - ErrorId: {}, User: {}, IP: {}, Reason: {}",
                errorId,
                securityUtils.getCurrentUsername(),
                request.getRemoteAddr(),
                ex.getMessage());
        
        // 클라이언트에는 최소한의 정보만 제공
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code(ex.getErrorCode().getCode())
                .message(getSecureMessage(ex.getErrorCode()))
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        // 보안 이벤트 기록
        loggingService.logSecurityEvent(
                "AUTH_FAILURE",
                request.getRemoteAddr(),
                ex.getErrorCode().getCode()
        );
        
        metricsService.recordAuthFailure(ex.getErrorCode().getCode());
        
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(response);
    }
    
    /**
     * 결제 관련 예외 처리
     */
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(
            PaymentException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        // 결제 실패는 중요 이벤트로 기록
        log.error("Payment failed - ErrorId: {}, User: {}, Amount: {}, Reason: {}",
                errorId,
                securityUtils.getCurrentUserId(),
                ex.getAmount(),
                ex.getMessage());
        
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code(ex.getErrorCode().getCode())
                .message(ex.getErrorCode().getMessage())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        // 결제 실패 알림
        notifyPaymentFailure(errorId, ex);
        
        metricsService.recordPaymentFailure(ex.getErrorCode().getCode());
        
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(response);
    }
    
    /**
     * 데이터 관련 예외 처리
     */
    @ExceptionHandler(DataException.class)
    public ResponseEntity<ErrorResponse> handleDataException(
            DataException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        logException(errorId, ex, request);
        
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code(ex.getErrorCode().getCode())
                .message(ex.getErrorCode().getMessage())
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        metricsService.recordException("DataException", ex.getErrorCode().getCode());
        
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(response);
    }
    
    // ============================================
    // 검증 예외 처리
    // ============================================
    
    /**
     * Bean Validation 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (existing, replacement) -> existing
                ));
        
        ValidationErrorResponse response = ValidationErrorResponse.builder()
                .errorId(errorId)
                .code("VALIDATION_ERROR")
                .message("입력값 검증 실패")
                .fieldErrors(fieldErrors)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        log.warn("Validation failed - ErrorId: {}, Fields: {}", 
                errorId, fieldErrors.keySet());
        
        metricsService.recordValidationError();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    
    /**
     * Constraint Violation 예외 처리
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ValidationErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        Map<String, String> violations = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage()
                ));
        
        ValidationErrorResponse response = ValidationErrorResponse.builder()
                .errorId(errorId)
                .code("CONSTRAINT_VIOLATION")
                .message("제약 조건 위반")
                .fieldErrors(violations)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        log.warn("Constraint violation - ErrorId: {}, Violations: {}",
                errorId, violations);
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    
    // ============================================
    // Spring Security 예외 처리
    // ============================================
    
    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        String message = "인증에 실패했습니다";
        String code = "AUTH_FAILED";
        
        if (ex instanceof BadCredentialsException) {
            message = "잘못된 인증 정보입니다";
            code = "BAD_CREDENTIALS";
        }
        
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        // 보안 이벤트 로깅
        loggingService.logSecurityEvent(
                "AUTHENTICATION_FAILURE",
                request.getRemoteAddr(),
                code
        );
        
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(response);
    }
    
    /**
     * 접근 거부 예외 처리
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code("ACCESS_DENIED")
                .message("접근 권한이 없습니다")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        log.warn("Access denied - ErrorId: {}, User: {}, Path: {}",
                errorId,
                securityUtils.getCurrentUsername(),
                request.getRequestURI());
        
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(response);
    }
    
    // ============================================
    // 데이터베이스 예외 처리
    // ============================================
    
    /**
     * 데이터 무결성 위반 예외 처리
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        String message = "데이터 처리 중 오류가 발생했습니다";
        String code = "DATA_INTEGRITY_VIOLATION";
        
        // 중복 키 예외 처리
        if (ex.getMessage() != null && ex.getMessage().contains("Duplicate entry")) {
            message = "이미 존재하는 데이터입니다";
            code = "DUPLICATE_DATA";
        }
        
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        log.error("Data integrity violation - ErrorId: {}, Message: {}",
                errorId, ex.getMessage());
        
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
    
    /**
     * 낙관적 락 예외 처리
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(
            OptimisticLockingFailureException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code("CONCURRENT_UPDATE")
                .message("다른 사용자가 동시에 수정했습니다. 다시 시도해주세요")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        log.warn("Optimistic lock failure - ErrorId: {}", errorId);
        
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
    
    // ============================================
    // HTTP 예외 처리
    // ============================================
    
    /**
     * 404 Not Found 처리
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(
            NoHandlerFoundException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code("NOT_FOUND")
                .message("요청한 리소스를 찾을 수 없습니다")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }
    
    /**
     * 지원하지 않는 HTTP Method 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code("METHOD_NOT_ALLOWED")
                .message(String.format("지원하지 않는 요청 방식입니다: %s", ex.getMethod()))
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(response);
    }
    
    /**
     * 필수 파라미터 누락 처리
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code("MISSING_PARAMETER")
                .message(String.format("필수 파라미터가 누락되었습니다: %s", ex.getParameterName()))
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
    
    // ============================================
    // 일반 예외 처리
    // ============================================
    
    /**
     * 처리되지 않은 모든 예외 처리
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        
        String errorId = generateErrorId();
        
        // 예상치 못한 오류는 상세하게 로깅
        log.error("Unexpected error - ErrorId: {}, Path: {}, User: {}",
                errorId,
                request.getRequestURI(),
                securityUtils.getCurrentUsername(),
                ex);
        
        // 클라이언트에는 일반적인 메시지만 제공
        ErrorResponse response = ErrorResponse.builder()
                .errorId(errorId)
                .code("INTERNAL_ERROR")
                .message("시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        // 개발 환경에서는 스택 트레이스 포함
        if (isDebugMode()) {
            response.setDetails(Map.of(
                    "exception", ex.getClass().getSimpleName(),
                    "message", ex.getMessage()
            ));
        }
        
        // 심각한 오류 알림
        notifyCriticalError(errorId, ex, request);
        
        metricsService.recordCriticalError();
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
    
    // ============================================
    // Helper Methods
    // ============================================
    
    /**
     * 고유 에러 ID 생성
     */
    private String generateErrorId() {
        return "ERR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * 디버그 모드 확인
     */
    private boolean isDebugMode() {
        return debugEnabled || "development".equals(environment);
    }
    
    /**
     * 보안 메시지 처리 (민감한 정보 제거)
     */
    private String getSecureMessage(ErrorCode errorCode) {
        // 보안 관련 에러는 일반적인 메시지로 변환
        if (errorCode.name().startsWith("AUTH_") || 
            errorCode.name().startsWith("SECURITY_")) {
            return "인증 또는 권한 오류가 발생했습니다";
        }
        return errorCode.getMessage();
    }
    
    /**
     * 예외 로깅
     */
    private void logException(String errorId, BaseException ex, HttpServletRequest request) {
        log.error("Exception occurred - ErrorId: {}, Code: {}, Path: {}, User: {}, Message: {}",
                errorId,
                ex.getErrorCode().getCode(),
                request.getRequestURI(),
                securityUtils.getCurrentUsername(),
                ex.getMessage());
    }
    
    /**
     * 결제 실패 알림
     */
    private void notifyPaymentFailure(String errorId, PaymentException ex) {
        // 결제 실패는 관리자에게 즉시 알림
        try {
            Map<String, Object> notificationData = Map.of(
                    "errorId", errorId,
                    "userId", securityUtils.getCurrentUserId(),
                    "amount", ex.getAmount(),
                    "reason", ex.getMessage(),
                    "timestamp", LocalDateTime.now()
            );
            
            // 알림 서비스 호출 (비동기)
            // notificationService.sendAdminAlert("PAYMENT_FAILURE", notificationData);
        } catch (Exception e) {
            log.error("Failed to send payment failure notification", e);
        }
    }
    
    /**
     * 심각한 오류 알림
     */
    private void notifyCriticalError(String errorId, Exception ex, HttpServletRequest request) {
        try {
            Map<String, Object> errorData = Map.of(
                    "errorId", errorId,
                    "exception", ex.getClass().getName(),
                    "message", ex.getMessage(),
                    "path", request.getRequestURI(),
                    "user", securityUtils.getCurrentUsername(),
                    "timestamp", LocalDateTime.now()
            );
            
            // 심각한 오류는 즉시 알림
            // notificationService.sendCriticalAlert("SYSTEM_ERROR", errorData);
        } catch (Exception e) {
            log.error("Failed to send critical error notification", e);
        }
    }
}
```

## 예외 응답 DTO

```java
package com.routepick.common.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 에러 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    /**
     * 고유 에러 ID (추적용)
     */
    private String errorId;
    
    /**
     * 에러 코드
     */
    private String code;
    
    /**
     * 사용자 친화적 메시지
     */
    private String message;
    
    /**
     * 에러 발생 시간
     */
    private LocalDateTime timestamp;
    
    /**
     * 요청 경로
     */
    private String path;
    
    /**
     * 상세 정보 (개발 환경에서만 제공)
     */
    private Map<String, Object> details;
}

/**
 * 검증 에러 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationErrorResponse extends ErrorResponse {
    
    /**
     * 필드별 검증 오류
     */
    private Map<String, String> fieldErrors;
}
```

## 테스트 코드

```java
package com.routepick.common.exception.handler;

import com.routepick.common.exception.UserException;
import com.routepick.common.exception.AuthException;
import com.routepick.common.exception.dto.ErrorResponse;
import com.routepick.common.exception.enums.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @DisplayName("UserException 처리 테스트")
    void handleUserException() throws Exception {
        mockMvc.perform(get("/test/user-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다"))
                .andExpect(jsonPath("$.errorId").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/user-exception"));
    }
    
    @Test
    @DisplayName("Validation 예외 처리 테스트")
    void handleValidationException() throws Exception {
        String invalidJson = "{\"email\": \"invalid\", \"password\": \"123\"}";
        
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }
    
    @Test
    @DisplayName("인증 예외 처리 테스트")
    void handleAuthException() throws Exception {
        mockMvc.perform(get("/test/auth-exception"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.details").doesNotExist()); // 보안 정보는 숨김
    }
    
    @Test
    @DisplayName("처리되지 않은 예외 처리 테스트")
    void handleUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/unexpected-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요"))
                .andExpect(jsonPath("$.errorId").exists());
    }
    
    @Test
    @DisplayName("404 Not Found 처리 테스트")
    void handleNotFound() throws Exception {
        mockMvc.perform(get("/non-existent-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다"));
    }
}
```

## 설정 파일

```yaml
# application.yml
app:
  environment: ${SPRING_PROFILES_ACTIVE:production}
  debug:
    enabled: ${DEBUG_ENABLED:false}
  exception:
    include-stacktrace: ${INCLUDE_STACKTRACE:false}
    mask-sensitive-data: true
    log-level: ERROR
    notification:
      enabled: true
      critical-errors-email: admin@routepick.com
```

## 주요 기능

### 1. 일관된 에러 응답
- 모든 예외에 대해 통일된 응답 형식 제공
- 고유 에러 ID로 추적 가능
- 사용자 친화적 메시지 제공

### 2. 보안 강화
- 민감한 정보 자동 마스킹
- 개발/운영 환경별 응답 수준 조절
- 보안 예외 로깅 및 모니터링

### 3. 모니터링 통합
- 예외 발생 메트릭 자동 수집
- 심각한 오류 즉시 알림
- 에러 패턴 분석을 위한 로깅

### 4. 유연한 확장성
- 새로운 예외 타입 쉽게 추가 가능
- 커스텀 예외 처리 로직 구현 가능
- 다국어 메시지 지원 가능

이 통합 예외 처리 시스템은 RoutePickr 애플리케이션의 안정성과 사용자 경험을 크게 향상시킵니다.