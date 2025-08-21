# Step 3-3a: GlobalExceptionHandler 핵심 구현

> RoutePickr 전역 예외 처리 핵심 시스템 구현  
> 생성일: 2025-08-21  
> 기반 분석: step3-1_exception_base.md, step3-2_domain_exceptions.md  
> 세분화: step3-3_global_handler_security.md에서 분리

---

## 🎯 GlobalExceptionHandler 개요

### 설계 원칙
- **@ControllerAdvice**: Spring Boot 전역 예외 처리
- **표준 응답**: ApiResponse<T> 통일된 응답 형식
- **HTTP 매핑**: ErrorCode별 적절한 HTTP 상태 코드
- **보안 강화**: 민감정보 마스킹 및 보안 로깅
- **모니터링**: 예외 발생 빈도 추적 및 알림

### 4계층 예외 처리 아키텍처
```
┌─────────────────────┐
│ GlobalExceptionHandler │  ← @ControllerAdvice 전역 처리
│ (HTTP 응답 변환)       │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│  Domain Exceptions  │  ← 도메인별 구체 예외
│  (8개 예외 클래스)    │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│   BaseException     │  ← 추상 기본 클래스
│   (공통 기능)        │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│   ErrorCode Enum    │  ← 상세 에러 코드
│   (177개 에러 코드)   │
└─────────────────────┘
```

---

## 🔧 GlobalExceptionHandler 클래스

### 핵심 구조
```java
package com.routepick.exception.handler;

import com.routepick.common.ApiResponse;
import com.routepick.common.ErrorCode;
import com.routepick.exception.*;
import com.routepick.exception.auth.AuthException;
import com.routepick.exception.user.UserException;
import com.routepick.exception.gym.GymException;
import com.routepick.exception.route.RouteException;
import com.routepick.exception.tag.TagException;
import com.routepick.exception.payment.PaymentException;
import com.routepick.exception.validation.ValidationException;
import com.routepick.exception.system.SystemException;
import com.routepick.security.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RoutePickr 전역 예외 처리기
 * 
 * 주요 기능:
 * - 8개 도메인 예외별 처리
 * - 표준 ApiResponse 응답 형식
 * - 보안 강화 (민감정보 마스킹)
 * - Rate Limiting 예외 처리
 * - Spring Validation 예외 처리
 * - 보안 로깅 및 모니터링
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    
    private final MessageSource messageSource;
    private final SecurityContextHolder securityContextHolder;
    private final ExceptionMonitoringService monitoringService;
    private final SensitiveDataMasker sensitiveDataMasker;
    
    @Value("${app.security.mask-sensitive-data:true}")
    private boolean maskSensitiveData;
    
    @Value("${app.security.detailed-error-info:false}")
    private boolean detailedErrorInfo;
    
    // ========== 도메인별 커스텀 예외 처리 ==========
    
    /**
     * 인증/인가 관련 예외 처리
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAuthException(
            AuthException ex, HttpServletRequest request) {
        
        logSecurityException(ex, request);
        monitoringService.recordSecurityException(ex);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // 보안상 민감한 정보는 제한적으로 노출
        if (isHighSecurityError(ex.getErrorCode())) {
            errorResponse = sanitizeSecurityError(errorResponse);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            errorResponse.getUserMessage(), 
            errorResponse.getErrorCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }
    
    /**
     * 사용자 관련 예외 처리
     */
    @ExceptionHandler(UserException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleUserException(
            UserException ex, HttpServletRequest request) {
        
        logBusinessException(ex, request);
        monitoringService.recordBusinessException(ex);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // 개인정보 마스킹 처리
        if (maskSensitiveData) {
            errorResponse = maskUserSensitiveData(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            errorResponse.getUserMessage(), 
            errorResponse.getErrorCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }
    
    /**
     * 체육관 관련 예외 처리
     */
    @ExceptionHandler(GymException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleGymException(
            GymException ex, HttpServletRequest request) {
        
        logBusinessException(ex, request);
        monitoringService.recordBusinessException(ex);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // GPS 좌표 정보 마스킹 (정확한 위치 노출 방지)
        if (maskSensitiveData && hasGpsInfo(ex)) {
            errorResponse = maskGpsCoordinates(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            errorResponse.getUserMessage(), 
            errorResponse.getErrorCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }
    
    /**
     * 루트 관련 예외 처리
     */
    @ExceptionHandler(RouteException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleRouteException(
            RouteException ex, HttpServletRequest request) {
        
        logBusinessException(ex, request);
        monitoringService.recordBusinessException(ex);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            errorResponse.getUserMessage(), 
            errorResponse.getErrorCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }
    
    /**
     * 태그 시스템 관련 예외 처리 (추천 시스템 포함)
     */
    @ExceptionHandler(TagException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleTagException(
            TagException ex, HttpServletRequest request) {
        
        logBusinessException(ex, request);
        monitoringService.recordBusinessException(ex);
        
        // 추천 시스템 관련 보안 예외 특별 처리
        if (isRecommendationSecurityViolation(ex)) {
            logSecurityException(ex, request);
            monitoringService.recordSecurityException(ex);
        }
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            errorResponse.getUserMessage(), 
            errorResponse.getErrorCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }
    
    /**
     * 결제 관련 예외 처리
     */
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handlePaymentException(
            PaymentException ex, HttpServletRequest request) {
        
        logBusinessException(ex, request);
        monitoringService.recordPaymentException(ex);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // 결제 정보 마스킹 (카드번호, 계좌번호 등)
        if (maskSensitiveData) {
            errorResponse = maskPaymentSensitiveData(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            errorResponse.getUserMessage(), 
            errorResponse.getErrorCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }
    
    /**
     * 입력 검증 관련 예외 처리 (보안 강화)
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        
        // XSS, SQL Injection 등 보안 위협 감지 시 특별 처리
        if (isSecurityThreat(ex)) {
            logSecurityThreat(ex, request);
            monitoringService.recordSecurityThreat(ex);
            
            // 심각한 보안 위협 시 알림 발송
            if (isCriticalSecurityThreat(ex)) {
                monitoringService.sendSecurityAlert(ex, request);
            }
        } else {
            logValidationException(ex, request);
        }
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // 악성 입력값 마스킹
        if (maskSensitiveData) {
            errorResponse = maskMaliciousInput(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            errorResponse.getUserMessage(), 
            errorResponse.getErrorCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }
    
    /**
     * 시스템 관련 예외 처리
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleSystemException(
            SystemException ex, HttpServletRequest request) {
        
        logSystemException(ex, request);
        monitoringService.recordSystemException(ex);
        
        // Rate Limiting 예외 특별 처리
        if (isRateLimitException(ex)) {
            return handleRateLimitException(ex, request);
        }
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // 시스템 내부 정보 제한적 노출
        if (!detailedErrorInfo) {
            errorResponse = sanitizeSystemError(errorResponse);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            errorResponse.getUserMessage(), 
            errorResponse.getErrorCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }
    
    // ========== Spring 표준 예외 처리 ==========
    
    /**
     * @Valid 검증 실패 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        logValidationException(ex, request);
        
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::createFieldErrorDetail)
            .collect(Collectors.toList());
        
        ValidationErrorResponse errorResponse = ValidationErrorResponse.builder()
            .errorCode("VALIDATION-001")
            .userMessage("입력 정보를 확인해주세요")
            .developerMessage("Validation failed for request body")
            .fieldErrors(fieldErrors)
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .build();
        
        ApiResponse<ValidationErrorResponse> response = ApiResponse.error(
            "입력 정보를 확인해주세요", 
            "VALIDATION-001"
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }
    
    /**
     * @Validated 검증 실패 예외 처리
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        logValidationException(ex, request);
        
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations()
            .stream()
            .map(this::createFieldErrorDetail)
            .collect(Collectors.toList());
        
        ValidationErrorResponse errorResponse = ValidationErrorResponse.builder()
            .errorCode("VALIDATION-001")
            .userMessage("입력 정보를 확인해주세요")
            .developerMessage("Constraint validation failed")
            .fieldErrors(fieldErrors)
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .build();
        
        ApiResponse<ValidationErrorResponse> response = ApiResponse.error(
            "입력 정보를 확인해주세요", 
            "VALIDATION-001"
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }
    
    /**
     * Spring Security 접근 거부 예외 처리
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        
        logSecurityException(ex, request);
        monitoringService.recordSecurityException(ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode("AUTH-041")
            .userMessage("접근 권한이 없습니다")
            .developerMessage("Access denied to requested resource")
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .build();
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            "접근 권한이 없습니다", 
            "AUTH-041"
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(response);
    }
    
    /**
     * HTTP 메서드 지원하지 않음 예외 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        
        logSystemException(ex, request);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode("COMMON-003")
            .userMessage("허용되지 않는 요청 방식입니다")
            .developerMessage("HTTP method not allowed: " + ex.getMethod())
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .supportedMethods(Arrays.asList(ex.getSupportedMethods()))
            .build();
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            "허용되지 않는 요청 방식입니다", 
            "COMMON-003"
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(response);
    }
    
    /**
     * 핸들러를 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {
        
        logSystemException(ex, request);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode("COMMON-002")
            .userMessage("요청한 리소스를 찾을 수 없습니다")
            .developerMessage("No handler found for " + ex.getHttpMethod() + " " + ex.getRequestURL())
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .build();
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            "요청한 리소스를 찾을 수 없습니다", 
            "COMMON-002"
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(response);
    }
    
    /**
     * 기타 모든 예외 처리 (최종 fallback)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        logSystemException(ex, request);
        monitoringService.recordUnhandledException(ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode("SYSTEM-001")
            .userMessage("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요")
            .developerMessage(detailedErrorInfo ? ex.getMessage() : "Internal server error")
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .build();
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요", 
            "SYSTEM-001"
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response);
    }
    
    // ========== Rate Limiting 특별 처리 ==========
    
    /**
     * Rate Limiting 예외 특별 처리
     */
    private ResponseEntity<ApiResponse<RateLimitErrorResponse>> handleRateLimitException(
            SystemException ex, HttpServletRequest request) {
        
        logSecurityException(ex, request);
        monitoringService.recordRateLimitViolation(ex, request);
        
        RateLimitInfo rateLimitInfo = extractRateLimitInfo(request);
        
        RateLimitErrorResponse errorResponse = RateLimitErrorResponse.builder()
            .errorCode(ex.getErrorCode().getCode())
            .userMessage(ex.getUserMessage())
            .developerMessage(ex.getDeveloperMessage())
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .rateLimitInfo(rateLimitInfo)
            .retryAfterSeconds(rateLimitInfo.getRetryAfterSeconds())
            .build();
        
        ApiResponse<RateLimitErrorResponse> response = ApiResponse.error(
            ex.getUserMessage(), 
            ex.getErrorCode().getCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(rateLimitInfo.getRetryAfterSeconds()))
            .header("X-RateLimit-Limit", String.valueOf(rateLimitInfo.getLimit()))
            .header("X-RateLimit-Remaining", String.valueOf(rateLimitInfo.getRemaining()))
            .header("X-RateLimit-Reset", String.valueOf(rateLimitInfo.getResetTime()))
            .body(response);
    }
    
    // ========== 보조 메서드 ==========
    
    /**
     * 기본 ErrorResponse 생성
     */
    private ErrorResponse createErrorResponse(BaseException ex, HttpServletRequest request) {
        return ErrorResponse.builder()
            .errorCode(ex.getErrorCode().getCode())
            .userMessage(ex.getUserMessage())
            .developerMessage(ex.getDeveloperMessage())
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .traceId(generateTraceId())
            .build();
    }
    
    /**
     * FieldError를 FieldErrorDetail로 변환
     */
    private FieldErrorDetail createFieldErrorDetail(FieldError fieldError) {
        return FieldErrorDetail.builder()
            .field(fieldError.getField())
            .rejectedValue(maskSensitiveData ? 
                sensitiveDataMasker.mask(String.valueOf(fieldError.getRejectedValue())) :
                String.valueOf(fieldError.getRejectedValue()))
            .message(fieldError.getDefaultMessage())
            .code(fieldError.getCode())
            .build();
    }
    
    /**
     * ConstraintViolation을 FieldErrorDetail로 변환
     */
    private FieldErrorDetail createFieldErrorDetail(ConstraintViolation<?> violation) {
        return FieldErrorDetail.builder()
            .field(violation.getPropertyPath().toString())
            .rejectedValue(maskSensitiveData ? 
                sensitiveDataMasker.mask(String.valueOf(violation.getInvalidValue())) :
                String.valueOf(violation.getInvalidValue()))
            .message(violation.getMessage())
            .code(violation.getMessageTemplate())
            .build();
    }
    
    /**
     * 보안 관련 예외 판별
     */
    private boolean isHighSecurityError(ErrorCode errorCode) {
        String code = errorCode.getCode();
        return code.startsWith("AUTH-") && 
               (code.equals("AUTH-002") || code.equals("AUTH-007") || code.equals("AUTH-008"));
    }
    
    /**
     * 추천 시스템 보안 위반 판별
     */
    private boolean isRecommendationSecurityViolation(TagException ex) {
        String code = ex.getErrorCode().getCode();
        return code.equals("TAG-024") || code.equals("TAG-025") || code.equals("TAG-026"); // 새로운 보안 에러 코드
    }
    
    /**
     * 보안 위협 판별
     */
    private boolean isSecurityThreat(ValidationException ex) {
        String violationType = ex.getViolationType();
        return "XSS".equals(violationType) || 
               "SQL_INJECTION".equals(violationType) || 
               "MALICIOUS_REQUEST".equals(violationType);
    }
    
    /**
     * 심각한 보안 위협 판별
     */
    private boolean isCriticalSecurityThreat(ValidationException ex) {
        return "SQL_INJECTION".equals(ex.getViolationType());
    }
    
    /**
     * Rate Limiting 예외 판별
     */
    private boolean isRateLimitException(SystemException ex) {
        String code = ex.getErrorCode().getCode();
        return code.equals("SYSTEM-021") || code.equals("SYSTEM-022");
    }
    
    /**
     * GPS 정보 포함 여부 확인
     */
    private boolean hasGpsInfo(GymException ex) {
        return ex.getLatitude() != null && ex.getLongitude() != null;
    }
    
    /**
     * 추적 ID 생성
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    
    // ========== 로깅 메서드 ==========
    
    /**
     * 보안 예외 로깅
     */
    private void logSecurityException(Exception ex, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        String currentUser = securityContextHolder.getCurrentUsername();
        
        log.warn("Security Exception [IP: {}, User: {}, UserAgent: {}, Path: {}, Exception: {}]",
            maskSensitiveData ? sensitiveDataMasker.maskIpAddress(clientIp) : clientIp,
            currentUser,
            userAgent,
            request.getRequestURI(),
            ex.getClass().getSimpleName());
    }
    
    /**
     * 보안 위협 로깅
     */
    private void logSecurityThreat(ValidationException ex, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        
        log.error("SECURITY THREAT DETECTED [Type: {}, IP: {}, UserAgent: {}, Path: {}, Field: {}]",
            ex.getViolationType(),
            clientIp,
            userAgent,
            request.getRequestURI(),
            ex.getFieldName());
    }
    
    /**
     * 비즈니스 예외 로깅
     */
    private void logBusinessException(BaseException ex, HttpServletRequest request) {
        log.info("Business Exception [Code: {}, Path: {}, Message: {}]",
            ex.getErrorCode().getCode(),
            request.getRequestURI(),
            ex.getMessage());
    }
    
    /**
     * 검증 예외 로깅
     */
    private void logValidationException(Exception ex, HttpServletRequest request) {
        log.info("Validation Exception [Path: {}, Exception: {}]",
            request.getRequestURI(),
            ex.getClass().getSimpleName());
    }
    
    /**
     * 시스템 예외 로깅
     */
    private void logSystemException(Exception ex, HttpServletRequest request) {
        log.error("System Exception [Path: {}, Exception: {}, Message: {}]",
            request.getRequestURI(),
            ex.getClass().getSimpleName(),
            ex.getMessage(),
            ex);
    }
    
    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP", 
            "X-Forwarded",
            "X-Cluster-Client-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}
```

---

## 📋 ErrorResponse DTO 클래스

### 기본 ErrorResponse
```java
package com.routepick.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 표준 에러 응답 DTO
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    
    private String errorCode;           // 에러 코드 (AUTH-001, USER-001 등)
    private String userMessage;        // 한국어 사용자 메시지
    private String developerMessage;   // 영문 개발자 메시지
    private LocalDateTime timestamp;   // 에러 발생 시각
    private String path;               // 요청 경로
    private String traceId;            // 추적 ID
    private List<String> supportedMethods; // 지원되는 HTTP 메서드 (해당 시)
    
    // 보안 관련 추가 정보 (필요 시)
    private String securityLevel;      // 보안 수준 (HIGH, MEDIUM, LOW)
    private String ipAddress;          // 클라이언트 IP (마스킹됨)
    private String userAgent;          // User Agent (필요 시)
}
```

### ValidationErrorResponse
```java
package com.routepick.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 검증 에러 응답 DTO
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationErrorResponse {
    
    private String errorCode;           // 에러 코드
    private String userMessage;        // 한국어 사용자 메시지  
    private String developerMessage;   // 영문 개발자 메시지
    private LocalDateTime timestamp;   // 에러 발생 시각
    private String path;               // 요청 경로
    private List<FieldErrorDetail> fieldErrors; // 필드별 에러 상세
    private int totalErrors;           // 총 에러 개수
}

/**
 * 필드 에러 상세 정보
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldErrorDetail {
    
    private String field;              // 필드명
    private String rejectedValue;      // 입력된 값 (마스킹됨)
    private String message;            // 에러 메시지
    private String code;               // 에러 코드
    private String expectedFormat;     // 기대되는 형식
    private List<String> suggestions;  // 수정 제안사항
}
```

### RateLimitErrorResponse
```java
package com.routepick.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Rate Limiting 에러 응답 DTO
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateLimitErrorResponse {
    
    private String errorCode;           // 에러 코드
    private String userMessage;        // 한국어 사용자 메시지
    private String developerMessage;   // 영문 개발자 메시지
    private LocalDateTime timestamp;   // 에러 발생 시각
    private String path;               // 요청 경로
    private RateLimitInfo rateLimitInfo; // Rate Limit 상세 정보
    private long retryAfterSeconds;    // 재시도 가능 시간 (초)
}

/**
 * Rate Limit 상세 정보
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RateLimitInfo {
    
    private int limit;                 // 제한 횟수
    private int remaining;             // 남은 횟수
    private long resetTime;            // 리셋 시간 (epoch)
    private long retryAfterSeconds;    // 재시도 가능 시간 (초)
    private String limitType;          // 제한 타입 (IP, USER, API)
    private String rateLimitKey;       // Rate Limit 키 (마스킹됨)
}
```

---

## ⚙️ Spring Boot 통합 설정

### application.yml 설정
```yaml
# 예외 처리 관련 설정
app:
  security:
    mask-sensitive-data: ${MASK_SENSITIVE_DATA:true}
    detailed-error-info: ${DETAILED_ERROR_INFO:false}
    rate-limit:
      enabled: true
      default-limit: 100
      default-window: PT1M  # 1분
  monitoring:
    security-alert-threshold: ${SECURITY_ALERT_THRESHOLD:5}
    system-error-threshold: ${SYSTEM_ERROR_THRESHOLD:10}
    slack:
      webhook-url: ${SLACK_WEBHOOK_URL:}
      security-channel: ${SLACK_SECURITY_CHANNEL:#security-alerts}
      system-channel: ${SLACK_SYSTEM_CHANNEL:#system-alerts}

# 환경별 로깅 설정
logging:
  level:
    com.routepick.exception: INFO
    com.routepick.security: WARN
    com.routepick.monitoring: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level [%logger{36}] - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%logger{36}] [%X{traceId}] - %msg%n"

---
# 개발 환경
spring:
  profiles: local
app:
  security:
    detailed-error-info: true  # 개발 환경에서는 상세 에러 정보 표시
logging:
  level:
    com.routepick.exception: DEBUG
    com.routepick.security: DEBUG

---
# 스테이징 환경  
spring:
  profiles: staging
app:
  security:
    detailed-error-info: false
logging:
  level:
    com.routepick.exception: INFO
    com.routepick.security: WARN

---
# 운영 환경
spring:
  profiles: production
app:
  security:
    mask-sensitive-data: true
    detailed-error-info: false
  monitoring:
    security-alert-threshold: 3  # 운영에서는 더 엄격하게
    system-error-threshold: 5
logging:
  level:
    com.routepick.exception: WARN
    com.routepick.security: ERROR
  file:
    name: logs/routepick-errors.log
    max-size: 100MB
    max-history: 30
```

---

## ✅ Step 3-3a 완료 체크리스트

### 🔧 GlobalExceptionHandler 구현
- [x] **@ControllerAdvice**: Spring Boot 전역 예외 처리 구현
- [x] **8개 도메인 예외**: 각 커스텀 예외별 @ExceptionHandler 구현
- [x] **HTTP 상태 매핑**: ErrorCode별 적절한 HTTP 상태 코드 자동 매핑
- [x] **ApiResponse 연동**: 표준 응답 형식 완전 적용
- [x] **Spring 표준 예외**: @Valid, @Validated, AccessDenied 등 처리

### 📋 예외 응답 구조
- [x] **ErrorResponse DTO**: 기본 에러 응답 구조 설계
- [x] **ValidationErrorResponse**: 필드별 검증 에러 정보 포함
- [x] **RateLimitErrorResponse**: Rate Limiting 상세 정보 포함
- [x] **FieldErrorDetail**: 필드별 상세 에러 정보
- [x] **타임스탬프/경로**: 모든 응답에 요청 정보 포함

### ⚙️ Spring Boot 통합
- [x] **application.yml**: 환경별 에러 설정 (local/staging/production)
- [x] **로깅 레벨**: 환경별 차등 로깅 레벨 설정
- [x] **보안 설정**: 민감정보 마스킹, 상세 에러 정보 환경별 제어
- [x] **Rate Limit 설정**: Redis 기반 분산 Rate Limiting
- [x] **모니터링 설정**: Slack 웹훅, 알림 임계값 설정

---

**다음 단계**: step3-3b_security_features.md (보안 강화 기능)  
**관련 파일**: step3-3c_monitoring_testing.md (모니터링 및 테스트)

*생성일: 2025-08-21*  
*핵심 성과: RoutePickr 전역 예외 처리 핵심 시스템 완성*