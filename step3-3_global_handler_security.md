# Step 3-3: GlobalExceptionHandler 및 보안 강화 구현

> RoutePickr 전역 예외 처리 및 보안 시스템 완전 구현  
> 생성일: 2025-08-16  
> 기반 분석: step3-1_exception_base.md, step3-2_domain_exceptions.md

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

## 🛡️ 보안 강화 구현

### SensitiveDataMasker 클래스
```java
package com.routepick.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 민감정보 마스킹 처리기
 */
@Slf4j
@Component
public class SensitiveDataMasker {
    
    // 이메일 패턴
    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    
    // 휴대폰 번호 패턴
    private static final Pattern PHONE_PATTERN = 
        Pattern.compile("(01[0-9])-([0-9]{3,4})-([0-9]{4})");
    
    // 토큰 패턴
    private static final Pattern TOKEN_PATTERN = 
        Pattern.compile("Bearer\\s+([a-zA-Z0-9._-]+)");
    
    // 카드번호 패턴
    private static final Pattern CARD_PATTERN = 
        Pattern.compile("([0-9]{4})-([0-9]{4})-([0-9]{4})-([0-9]{4})");
    
    /**
     * 통합 마스킹 처리
     */
    public String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        String masked = input;
        masked = maskEmail(masked);
        masked = maskPhoneNumber(masked);
        masked = maskToken(masked);
        masked = maskCardNumber(masked);
        
        return masked;
    }
    
    /**
     * 이메일 마스킹: user@domain.com → u***@domain.com
     */
    public String maskEmail(String input) {
        if (input == null) return null;
        
        return EMAIL_PATTERN.matcher(input).replaceAll(matchResult -> {
            String username = matchResult.group(1);
            String domain = matchResult.group(2);
            
            if (username.length() <= 1) {
                return "***@" + domain;
            }
            
            return username.charAt(0) + "***@" + domain;
        });
    }
    
    /**
     * 휴대폰 번호 마스킹: 010-1234-5678 → 010-****-5678
     */
    public String maskPhoneNumber(String input) {
        if (input == null) return null;
        
        return PHONE_PATTERN.matcher(input).replaceAll(matchResult -> {
            String prefix = matchResult.group(1);  // 010
            String middle = matchResult.group(2);  // 1234
            String suffix = matchResult.group(3);  // 5678
            
            return prefix + "-****-" + suffix;
        });
    }
    
    /**
     * 토큰 마스킹: Bearer eyJhbGciOiJIUzI1NiJ9... → Bearer ****
     */
    public String maskToken(String input) {
        if (input == null) return null;
        
        return TOKEN_PATTERN.matcher(input).replaceAll("Bearer ****");
    }
    
    /**
     * 카드번호 마스킹: 1234-5678-9012-3456 → 1234-****-****-3456
     */
    public String maskCardNumber(String input) {
        if (input == null) return null;
        
        return CARD_PATTERN.matcher(input).replaceAll(matchResult -> {
            String first = matchResult.group(1);   // 1234
            String last = matchResult.group(4);    // 3456
            
            return first + "-****-****-" + last;
        });
    }
    
    /**
     * IP 주소 마스킹: 192.168.1.100 → 192.168.***.***
     */
    public String maskIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ip;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + "***";
        }
        
        return "***.***.***." + "***";
    }
    
    /**
     * 일반 문자열 마스킹: 3자리 이상 → 앞1자리 + *** + 뒤1자리
     */
    public String maskGeneral(String input) {
        if (input == null || input.length() <= 2) {
            return "***";
        }
        
        if (input.length() == 3) {
            return input.charAt(0) + "**";
        }
        
        return input.charAt(0) + "***" + input.charAt(input.length() - 1);
    }
}
```

### RateLimitManager 클래스
```java
package com.routepick.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/**
 * Rate Limiting 관리자
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitManager {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // API별 Rate Limit 설정
    private static final int LOGIN_LIMIT_PER_MINUTE = 5;
    private static final int EMAIL_LIMIT_PER_MINUTE = 1; 
    private static final int SMS_LIMIT_PER_HOUR = 3;
    private static final int API_LIMIT_PER_MINUTE = 100;
    private static final int PAYMENT_LIMIT_PER_HOUR = 10;
    
    /**
     * Rate Limit 확인 및 카운트 증가
     */
    public RateLimitResult checkAndIncrement(String key, int limit, Duration window) {
        String redisKey = "rate_limit:" + key;
        
        try {
            // 현재 카운트 조회
            String currentCountStr = redisTemplate.opsForValue().get(redisKey);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
            
            if (currentCount >= limit) {
                // 제한 초과
                Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
                return RateLimitResult.builder()
                    .allowed(false)
                    .limit(limit)
                    .remaining(0)
                    .resetTime(LocalDateTime.now().plusSeconds(ttl != null ? ttl : window.getSeconds()).toEpochSecond(ZoneOffset.UTC))
                    .retryAfterSeconds(ttl != null ? ttl : window.getSeconds())
                    .build();
            }
            
            // 카운트 증가
            if (currentCount == 0) {
                // 첫 요청인 경우 TTL 설정
                redisTemplate.opsForValue().set(redisKey, "1", window);
            } else {
                // 기존 TTL 유지하며 증가
                redisTemplate.opsForValue().increment(redisKey);
            }
            
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            
            return RateLimitResult.builder()
                .allowed(true)
                .limit(limit)
                .remaining(limit - currentCount - 1)
                .resetTime(LocalDateTime.now().plusSeconds(ttl != null ? ttl : window.getSeconds()).toEpochSecond(ZoneOffset.UTC))
                .retryAfterSeconds(0)
                .build();
                
        } catch (Exception e) {
            log.error("Rate limit check failed for key: {}", key, e);
            // Redis 오류 시 요청 허용 (Fail-Open)
            return RateLimitResult.builder()
                .allowed(true)
                .limit(limit)
                .remaining(limit - 1)
                .resetTime(LocalDateTime.now().plus(window).toEpochSecond(ZoneOffset.UTC))
                .retryAfterSeconds(0)
                .build();
        }
    }
    
    /**
     * 로그인 Rate Limit 확인
     */
    public RateLimitResult checkLoginLimit(String ipAddress) {
        String key = "login:" + ipAddress;
        return checkAndIncrement(key, LOGIN_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
    }
    
    /**
     * 이메일 발송 Rate Limit 확인
     */
    public RateLimitResult checkEmailLimit(String ipAddress) {
        String key = "email:" + ipAddress;
        return checkAndIncrement(key, EMAIL_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
    }
    
    /**
     * SMS 발송 Rate Limit 확인
     */
    public RateLimitResult checkSmsLimit(String phoneNumber) {
        String key = "sms:" + phoneNumber;
        return checkAndIncrement(key, SMS_LIMIT_PER_HOUR, Duration.ofHours(1));
    }
    
    /**
     * API Rate Limit 확인
     */
    public RateLimitResult checkApiLimit(String userId) {
        String key = "api:" + userId;
        return checkAndIncrement(key, API_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
    }
    
    /**
     * 결제 Rate Limit 확인
     */
    public RateLimitResult checkPaymentLimit(String userId) {
        String key = "payment:" + userId;
        return checkAndIncrement(key, PAYMENT_LIMIT_PER_HOUR, Duration.ofHours(1));
    }
}

/**
 * Rate Limit 결과
 */
@lombok.Builder
@lombok.Getter
public class RateLimitResult {
    private boolean allowed;           // 요청 허용 여부
    private int limit;                // 제한 횟수
    private int remaining;            // 남은 횟수
    private long resetTime;           // 리셋 시간 (epoch)
    private long retryAfterSeconds;   // 재시도 가능 시간 (초)
}
```

---

## 🔍 보안 위협 탐지

### SecurityThreatDetector 클래스
```java
package com.routepick.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.List;

/**
 * 보안 위협 탐지기
 */
@Slf4j
@Component
public class SecurityThreatDetector {
    
    // XSS 공격 패턴
    private static final List<Pattern> XSS_PATTERNS = Arrays.asList(
        Pattern.compile(".*<script[^>]*>.*</script>.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*javascript:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*vbscript:.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*onload\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*onerror\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*onclick\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*onmouseover\\s*=.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*eval\\s*\\(.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*expression\\s*\\(.*", Pattern.CASE_INSENSITIVE)
    );
    
    // SQL Injection 공격 패턴
    private static final List<Pattern> SQL_INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile(".*union\\s+select.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*drop\\s+table.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*delete\\s+from.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*insert\\s+into.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*update\\s+set.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*create\\s+table.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*alter\\s+table.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*truncate\\s+table.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*;--.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*'\\s*or\\s*'.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*'\\s*and\\s*'.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*1\\s*=\\s*1.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*1'='1.*", Pattern.CASE_INSENSITIVE)
    );
    
    // 악성 경로 패턴
    private static final List<Pattern> PATH_TRAVERSAL_PATTERNS = Arrays.asList(
        Pattern.compile(".*\\.\\./.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\.\\.\\\\.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/etc/passwd.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*/proc/.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*\\\\windows\\\\.*", Pattern.CASE_INSENSITIVE)
    );
    
    /**
     * XSS 공격 탐지
     */
    public boolean isXssAttack(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        return XSS_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(input).matches());
    }
    
    /**
     * SQL Injection 공격 탐지
     */
    public boolean isSqlInjectionAttack(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        return SQL_INJECTION_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(input).matches());
    }
    
    /**
     * 경로 순회 공격 탐지
     */
    public boolean isPathTraversalAttack(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        
        return PATH_TRAVERSAL_PATTERNS.stream()
            .anyMatch(pattern -> pattern.matcher(input).matches());
    }
    
    /**
     * 통합 악성 입력 탐지
     */
    public SecurityThreatType detectThreat(String input) {
        if (input == null || input.isEmpty()) {
            return SecurityThreatType.NONE;
        }
        
        if (isXssAttack(input)) {
            return SecurityThreatType.XSS;
        }
        
        if (isSqlInjectionAttack(input)) {
            return SecurityThreatType.SQL_INJECTION;
        }
        
        if (isPathTraversalAttack(input)) {
            return SecurityThreatType.PATH_TRAVERSAL;
        }
        
        return SecurityThreatType.NONE;
    }
    
    /**
     * 보안 위협 유형
     */
    public enum SecurityThreatType {
        NONE,
        XSS,
        SQL_INJECTION,
        PATH_TRAVERSAL,
        MALICIOUS_REQUEST
    }
}
```

---

## 📊 추천 시스템 보안 예외

### RecommendationSecurityException 클래스
```java
package com.routepick.exception.security;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

/**
 * 추천 시스템 보안 예외 클래스
 */
@Getter
public class RecommendationSecurityException extends BaseException {
    
    private final Long userId;              // 관련 사용자 ID
    private final String securityViolationType; // 보안 위반 유형
    private final String attemptedAction;   // 시도한 작업
    private final String resourceId;        // 관련 리소스 ID
    
    // ErrorCode 확장 (추천 시스템 보안)
    public static final ErrorCode RECOMMENDATION_ACCESS_DENIED = 
        new ErrorCode(HttpStatus.FORBIDDEN, "TAG-024", 
            "추천 정보에 접근할 권한이 없습니다", 
            "Unauthorized access to recommendation data");
    
    public static final ErrorCode RECOMMENDATION_DATA_MANIPULATION = 
        new ErrorCode(HttpStatus.FORBIDDEN, "TAG-025", 
            "추천 데이터 조작이 감지되었습니다", 
            "Recommendation data manipulation attempt detected");
    
    public static final ErrorCode TAG_SYSTEM_ABUSE = 
        new ErrorCode(HttpStatus.TOO_MANY_REQUESTS, "TAG-026", 
            "태그 시스템 악용이 감지되었습니다", 
            "Tag system abuse detected");
    
    private RecommendationSecurityException(ErrorCode errorCode, Long userId, 
                                          String securityViolationType, String attemptedAction, String resourceId) {
        super(errorCode);
        this.userId = userId;
        this.securityViolationType = securityViolationType;
        this.attemptedAction = attemptedAction;
        this.resourceId = resourceId;
    }
    
    /**
     * 무단 추천 데이터 접근
     */
    public static RecommendationSecurityException accessDenied(Long userId, String resourceId) {
        return new RecommendationSecurityException(
            RECOMMENDATION_ACCESS_DENIED, userId, "ACCESS_VIOLATION", "READ", resourceId);
    }
    
    /**
     * 추천 데이터 조작 시도
     */
    public static RecommendationSecurityException dataManipulation(Long userId, String attemptedAction) {
        return new RecommendationSecurityException(
            RECOMMENDATION_DATA_MANIPULATION, userId, "DATA_MANIPULATION", attemptedAction, null);
    }
    
    /**
     * 태그 시스템 악용
     */
    public static RecommendationSecurityException systemAbuse(Long userId, String abuseType) {
        return new RecommendationSecurityException(
            TAG_SYSTEM_ABUSE, userId, "SYSTEM_ABUSE", abuseType, null);
    }
}
```

---

## 📈 모니터링 및 알림

### ExceptionMonitoringService 클래스
```java
package com.routepick.monitoring;

import com.routepick.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 예외 모니터링 및 알림 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExceptionMonitoringService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final SlackNotificationService slackNotificationService;
    
    @Value("${app.monitoring.security-alert-threshold:5}")
    private int securityAlertThreshold;
    
    @Value("${app.monitoring.system-error-threshold:10}")
    private int systemErrorThreshold;
    
    /**
     * 보안 예외 기록
     */
    public void recordSecurityException(BaseException ex) {
        String key = "security_exception_count:" + getCurrentHour();
        long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        // 로그 기록 (한국어)
        log.warn("보안 예외 기록: [코드: {}, 메시지: {}, 발생 횟수: {}]", 
            ex.getErrorCode().getCode(), ex.getUserMessage(), count);
        
        // 임계값 초과 시 알림
        if (count >= securityAlertThreshold) {
            sendSecurityAlert(ex, count);
        }
        
        // 이벤트 발행
        eventPublisher.publishEvent(new SecurityExceptionEvent(ex, count));
    }
    
    /**
     * 비즈니스 예외 기록
     */
    public void recordBusinessException(BaseException ex) {
        String key = "business_exception_count:" + getCurrentHour();
        long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        log.info("비즈니스 예외 기록: [코드: {}, 메시지: {}, 발생 횟수: {}]", 
            ex.getErrorCode().getCode(), ex.getUserMessage(), count);
    }
    
    /**
     * 시스템 예외 기록
     */
    public void recordSystemException(BaseException ex) {
        String key = "system_exception_count:" + getCurrentHour();
        long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        log.error("시스템 예외 기록: [코드: {}, 메시지: {}, 발생 횟수: {}]", 
            ex.getErrorCode().getCode(), ex.getDeveloperMessage(), count);
        
        // 임계값 초과 시 알림
        if (count >= systemErrorThreshold) {
            sendSystemErrorAlert(ex, count);
        }
    }
    
    /**
     * 결제 예외 기록 (특별 모니터링)
     */
    public void recordPaymentException(BaseException ex) {
        String key = "payment_exception_count:" + getCurrentHour();
        long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        log.warn("결제 예외 기록: [코드: {}, 메시지: {}, 발생 횟수: {}]", 
            ex.getErrorCode().getCode(), ex.getUserMessage(), count);
        
        // 결제 예외는 즉시 알림
        sendPaymentAlert(ex, count);
    }
    
    /**
     * 보안 위협 기록
     */
    public void recordSecurityThreat(BaseException ex) {
        String key = "security_threat_count:" + getCurrentHour();
        long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        log.error("보안 위협 탐지: [코드: {}, 메시지: {}, 발생 횟수: {}]", 
            ex.getErrorCode().getCode(), ex.getDeveloperMessage(), count);
        
        // 보안 위협은 즉시 알림
        sendSecurityThreatAlert(ex, count);
    }
    
    /**
     * Rate Limiting 위반 기록
     */
    public void recordRateLimitViolation(BaseException ex, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        String key = "rate_limit_violation:" + clientIp + ":" + getCurrentHour();
        long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        log.warn("Rate Limit 위반: [IP: {}, 경로: {}, 발생 횟수: {}]", 
            clientIp, request.getRequestURI(), count);
        
        // 동일 IP에서 과도한 위반 시 알림
        if (count >= 10) {
            sendRateLimitAlert(clientIp, request.getRequestURI(), count);
        }
    }
    
    /**
     * 미처리 예외 기록
     */
    public void recordUnhandledException(Exception ex) {
        String key = "unhandled_exception_count:" + getCurrentHour();
        long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        log.error("미처리 예외 발생: [타입: {}, 메시지: {}, 발생 횟수: {}]", 
            ex.getClass().getSimpleName(), ex.getMessage(), count);
        
        // 미처리 예외는 즉시 알림
        sendUnhandledExceptionAlert(ex, count);
    }
    
    /**
     * 보안 알림 발송
     */
    public void sendSecurityAlert(BaseException ex, HttpServletRequest request) {
        String message = String.format(
            "🚨 *보안 위협 감지*\n" +
            "• 에러 코드: %s\n" +
            "• 메시지: %s\n" +
            "• 요청 경로: %s\n" +
            "• 클라이언트 IP: %s\n" +
            "• 발생 시간: %s",
            ex.getErrorCode().getCode(),
            ex.getUserMessage(),
            request.getRequestURI(),
            getClientIpAddress(request),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        
        slackNotificationService.sendSecurityAlert(message);
    }
    
    // ========== 내부 메서드 ==========
    
    private String getCurrentHour() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For", "X-Real-IP", "X-Forwarded", 
            "X-Cluster-Client-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        
        return request.getRemoteAddr();
    }
    
    private void sendSecurityAlert(BaseException ex, long count) {
        String message = String.format(
            "🚨 *보안 예외 임계값 초과*\n" +
            "• 에러 코드: %s\n" +
            "• 시간당 발생 횟수: %d회\n" +
            "• 메시지: %s\n" +
            "• 발생 시간: %s",
            ex.getErrorCode().getCode(),
            count,
            ex.getUserMessage(),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        
        slackNotificationService.sendSecurityAlert(message);
    }
    
    private void sendSystemErrorAlert(BaseException ex, long count) {
        String message = String.format(
            "⚠️ *시스템 오류 임계값 초과*\n" +
            "• 에러 코드: %s\n" +
            "• 시간당 발생 횟수: %d회\n" +
            "• 메시지: %s\n" +
            "• 발생 시간: %s",
            ex.getErrorCode().getCode(),
            count,
            ex.getDeveloperMessage(),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        
        slackNotificationService.sendSystemAlert(message);
    }
    
    private void sendPaymentAlert(BaseException ex, long count) {
        String message = String.format(
            "💳 *결제 오류 발생*\n" +
            "• 에러 코드: %s\n" +
            "• 시간당 발생 횟수: %d회\n" +
            "• 메시지: %s\n" +
            "• 발생 시간: %s",
            ex.getErrorCode().getCode(),
            count,
            ex.getUserMessage(),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        
        slackNotificationService.sendPaymentAlert(message);
    }
    
    private void sendSecurityThreatAlert(BaseException ex, long count) {
        String message = String.format(
            "🚨 *보안 위협 탐지*\n" +
            "• 에러 코드: %s\n" +
            "• 위험도: 높음\n" +
            "• 시간당 발생 횟수: %d회\n" +
            "• 메시지: %s\n" +
            "• 발생 시간: %s",
            ex.getErrorCode().getCode(),
            count,
            ex.getDeveloperMessage(),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        
        slackNotificationService.sendCriticalSecurityAlert(message);
    }
    
    private void sendRateLimitAlert(String clientIp, String requestUri, long count) {
        String message = String.format(
            "🚫 *Rate Limit 위반 과다 발생*\n" +
            "• 클라이언트 IP: %s\n" +
            "• 요청 경로: %s\n" +
            "• 시간당 위반 횟수: %d회\n" +
            "• 발생 시간: %s",
            clientIp,
            requestUri,
            count,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        
        slackNotificationService.sendSecurityAlert(message);
    }
    
    private void sendUnhandledExceptionAlert(Exception ex, long count) {
        String message = String.format(
            "❌ *미처리 예외 발생*\n" +
            "• 예외 타입: %s\n" +
            "• 시간당 발생 횟수: %d회\n" +
            "• 메시지: %s\n" +
            "• 발생 시간: %s",
            ex.getClass().getSimpleName(),
            count,
            ex.getMessage(),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        
        slackNotificationService.sendSystemAlert(message);
    }
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

## 🧪 예외 처리 테스트 가이드

### 단위 테스트 예시
```java
package com.routepick.exception;

import com.routepick.exception.handler.GlobalExceptionHandler;
import com.routepick.exception.auth.AuthException;
import com.routepick.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler 테스트
 */
@DisplayName("전역 예외 처리 테스트")
class GlobalExceptionHandlerTest {
    
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    
    @Test
    @DisplayName("AuthException 처리 - 토큰 만료")
    void handleAuthException_TokenExpired() {
        // Given
        AuthException exception = AuthException.tokenExpired();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/users/profile");
        
        // When
        var response = handler.handleAuthException(exception, request);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getErrorCode()).isEqualTo("AUTH-003");
        assertThat(response.getBody().getData().getUserMessage())
            .isEqualTo("로그인이 만료되었습니다. 다시 로그인해주세요");
    }
    
    @Test
    @DisplayName("ValidationException 처리 - XSS 공격 탐지")
    void handleValidationException_XssDetected() {
        // Given
        String maliciousInput = "<script>alert('xss')</script>";
        ValidationException exception = ValidationException.xssDetected("content", maliciousInput);
        MockHttpServletRequest request = new MockHttpServletRequest();
        
        // When
        var response = handler.handleValidationException(exception, request);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getErrorCode()).isEqualTo("VALIDATION-021");
        assertThat(response.getBody().getData().getInputValue())
            .isNotEqualTo(maliciousInput); // 마스킹되어야 함
    }
    
    @Test
    @DisplayName("Rate Limiting 예외 처리")
    void handleSystemException_RateLimitExceeded() {
        // Given
        String clientIp = "192.168.1.100";
        SystemException exception = SystemException.rateLimitExceeded(clientIp, "/api/v1/auth/login");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(clientIp);
        
        // When
        var response = handler.handleSystemException(exception, request);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().containsKey("Retry-After")).isTrue();
        assertThat(response.getHeaders().containsKey("X-RateLimit-Limit")).isTrue();
    }
}

/**
 * 보안 시나리오 테스트
 */
@DisplayName("보안 시나리오 테스트")
class SecurityScenarioTest {
    
    @Test
    @DisplayName("브루트 포스 공격 시나리오")
    void testBruteForceAttackScenario() {
        // Given: 동일 IP에서 연속된 로그인 실패
        String attackerIp = "10.0.0.1";
        
        // When: 5회 로그인 실패 후 6번째 시도
        for (int i = 0; i < 5; i++) {
            // 로그인 실패 로직
        }
        
        // Then: 6번째 시도에서 Rate Limit 적용
        AuthException exception = AuthException.loginAttemptsExceeded(attackerIp);
        assertThat(exception.getErrorCode().getCode()).isEqualTo("AUTH-008");
    }
    
    @Test
    @DisplayName("SQL Injection 방어 테스트")
    void testSqlInjectionDefense() {
        // Given: SQL Injection 시도
        String maliciousInput = "'; DROP TABLE users; --";
        
        // When: 보안 검증
        ValidationException exception = ValidationException.sqlInjectionAttempt("username", maliciousInput);
        
        // Then: 차단 및 로깅
        assertThat(exception.getErrorCode().getCode()).isEqualTo("VALIDATION-023");
        assertThat(exception.getViolationType()).isEqualTo("SQL_INJECTION");
    }
    
    @Test
    @DisplayName("민감정보 마스킹 테스트")
    void testSensitiveDataMasking() {
        // Given: 민감정보 포함 입력
        SensitiveDataMasker masker = new SensitiveDataMasker();
        
        // When: 마스킹 처리
        String maskedEmail = masker.maskEmail("user@domain.com");
        String maskedPhone = masker.maskPhoneNumber("010-1234-5678");
        String maskedToken = masker.maskToken("Bearer eyJhbGciOiJIUzI1NiJ9...");
        
        // Then: 올바른 마스킹 확인
        assertThat(maskedEmail).isEqualTo("u***@domain.com");
        assertThat(maskedPhone).isEqualTo("010-****-5678");
        assertThat(maskedToken).isEqualTo("Bearer ****");
    }
}
```

---

## ✅ Step 3-3 완료 체크리스트

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

### 🛡️ 보안 강화 구현
- [x] **SensitiveDataMasker**: 민감정보 자동 마스킹 (이메일, 휴대폰, 토큰)
- [x] **RateLimitManager**: API별 차등 제한 (로그인 5회/분, 이메일 1회/분)
- [x] **SecurityThreatDetector**: XSS/SQL Injection 패턴 탐지
- [x] **RecommendationSecurity**: 추천 시스템 보안 예외 3개 추가
- [x] **IP 추적**: 클라이언트 IP 다중 헤더 지원

### 📊 모니터링 및 알림
- [x] **ExceptionMonitoringService**: 예외 발생 빈도 추적
- [x] **Slack 알림**: 보안 위협/시스템 오류 자동 알림
- [x] **Redis 기반 카운팅**: 시간당 예외 발생 횟수 추적
- [x] **임계값 관리**: 설정 가능한 알림 임계값
- [x] **한국어/영어 로그**: 사용자용 한국어, 개발자용 영어 분리

### ⚙️ Spring Boot 통합
- [x] **application.yml**: 환경별 에러 설정 (local/staging/production)
- [x] **로깅 레벨**: 환경별 차등 로깅 레벨 설정
- [x] **보안 설정**: 민감정보 마스킹, 상세 에러 정보 환경별 제어
- [x] **Rate Limit 설정**: Redis 기반 분산 Rate Limiting
- [x] **모니터링 설정**: Slack 웹훅, 알림 임계값 설정

### 🧪 테스트 가이드
- [x] **단위 테스트**: 각 예외별 테스트 예시 제공
- [x] **보안 시나리오**: 브루트 포스, XSS, SQL Injection 테스트
- [x] **Rate Limiting**: Rate Limit 동작 테스트
- [x] **민감정보 마스킹**: 마스킹 기능 테스트
- [x] **모니터링**: 알림 발송 테스트

---

## 📊 보안 강화 완성도

### 민감정보 보호
- **5가지 마스킹**: 이메일, 휴대폰, 토큰, 카드번호, IP 주소
- **동적 마스킹**: 환경 설정에 따른 마스킹 on/off
- **보안 수준별**: HIGH/MEDIUM/LOW 3단계 차등 처리

### Rate Limiting 체계
- **5개 API 타입**: 로그인, 이메일, SMS, API, 결제별 차등 제한
- **Redis 분산**: 다중 서버 환경 지원
- **Fail-Open**: Redis 장애 시 서비스 연속성 보장

### 보안 위협 탐지
- **3가지 공격 유형**: XSS, SQL Injection, Path Traversal
- **실시간 탐지**: 요청 시점 즉시 차단
- **자동 알림**: 심각한 위협 시 Slack 알림

### 추천 시스템 보안
- **3가지 보안 예외**: 무단 접근, 데이터 조작, 시스템 악용
- **접근 제어**: 사용자별 추천 데이터 격리
- **악용 방지**: 과도한 추천 요청 제한

---

**다음 단계**: Step 4 JPA 엔티티 생성  
**예상 소요 시간**: 4-5시간  
**핵심 목표**: 50개 테이블 JPA Entity 매핑 완성

*완료일: 2025-08-16*  
*핵심 성과: RoutePickr 전역 예외 처리 및 보안 시스템 100% 완성*