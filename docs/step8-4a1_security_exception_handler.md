# step8-4a1_security_exception_handler.md

## 🔧 SecurityExceptionHandler 구현

> RoutePickr 보안 예외 전담 처리기  
> 생성일: 2025-08-27  
> 분할: step8-4a_global_exception_handler.md → 3개 파일  
> 담당: CORS/CSRF/XSS/Rate Limiting 보안 예외 처리

---

## 🎯 보안 예외 처리 개요

### 설계 원칙
- **@RestControllerAdvice**: Spring Boot 보안 예외 전담 처리
- **8-3 보안 통합**: CORS, CSRF, XSS 예외 통합 관리
- **실시간 모니터링**: 보안 이벤트 즉시 추적 및 대응
- **자동 차단**: 위협 수준별 차등 IP 차단
- **민감정보 마스킹**: 응답 데이터 보안 강화

### 보안 예외 처리 아키텍처
```
┌─────────────────────┐
│ SecurityExceptionHandler │  ← 보안 예외 전담 처리
│ (@RestControllerAdvice)   │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│ SecurityMonitoringService │  ← 실시간 모니터링 연동
│ (위협 탐지 & 자동 차단)     │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│ Security Exceptions │  ← 보안 예외 클래스들
│ (CORS, CSRF, XSS...)    │
└─────────────────────┘
```

---

## 🔧 SecurityExceptionHandler 구현

### 보안 예외 전담 처리기
```java
package com.routepick.exception.handler;

import com.routepick.common.ApiResponse;
import com.routepick.exception.dto.ErrorResponse;
import com.routepick.exception.security.*;
import com.routepick.monitoring.SecurityMonitoringService;
import com.routepick.security.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 보안 예외 전담 처리기
 * 8-3 단계 보안 시스템과 연동
 * 
 * 담당 예외:
 * - CORS 위반
 * - CSRF 공격
 * - XSS 공격
 * - Rate Limiting 위반
 * - 입력 검증 실패
 */
@Slf4j
@RestControllerAdvice(value = "com.routepick.security")
@RequiredArgsConstructor
public class SecurityExceptionHandler {
    
    private final SecurityMonitoringService monitoringService;
    private final SensitiveDataMasker sensitiveDataMasker;
    
    @Value("${app.security.mask-sensitive-data:true}")
    private boolean maskSensitiveData;
    
    @Value("${app.security.auto-blocking:true}")
    private boolean autoBlocking;
    
    /**
     * CORS 위반 예외 처리
     */
    @ExceptionHandler(CorsViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleCorsViolation(
            CorsViolationException ex, HttpServletRequest request) {
        
        // 1. 즉시 IP 블록킹 검토
        String clientIp = getClientIpAddress(request);
        if (autoBlocking && ex.isSuspiciousActivity()) {
            monitoringService.applySecurityPenalty(clientIp, "CORS_VIOLATION", 600); // 10분
        }
        
        // 2. 보안 이벤트 기록
        logSecurityException(ex, request);
        monitoringService.recordCorsViolation(ex, request);
        
        // 3. 에러 응답 생성
        ErrorResponse errorResponse = createSecurityErrorResponse(ex, request);
        
        // 4. Origin 정보 마스킹
        if (maskSensitiveData) {
            errorResponse = maskOriginInfo(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            "허용되지 않은 도메인에서의 요청입니다", 
            ex.getErrorCode().getCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .header("Access-Control-Allow-Origin", "") // CORS 헤더 제거
            .body(response);
    }
    
    /**
     * CSRF 공격 예외 처리
     */
    @ExceptionHandler(CsrfViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleCsrfViolation(
            CsrfViolationException ex, HttpServletRequest request) {
        
        // 1. IP 차단 적용 (CSRF는 더 심각한 위협)
        String clientIp = getClientIpAddress(request);
        if (autoBlocking) {
            monitoringService.applySecurityPenalty(clientIp, "CSRF_VIOLATION", 900); // 15분
        }
        
        // 2. 보안 이벤트 기록 및 알림
        logSecurityException(ex, request);
        monitoringService.recordCsrfViolation(ex, request);
        monitoringService.sendSecurityAlert("CSRF 공격 탐지", ex, request);
        
        // 3. 에러 응답 생성
        ErrorResponse errorResponse = createSecurityErrorResponse(ex, request);
        
        // 4. 토큰 정보 완전 마스킹
        if (maskSensitiveData) {
            errorResponse = maskCsrfTokenInfo(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            "보안 토큰 검증에 실패했습니다. 페이지를 새로고침 후 다시 시도해주세요", 
            ex.getErrorCode().getCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(response);
    }
    
    /**
     * XSS 공격 예외 처리
     */
    @ExceptionHandler(XssViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleXssViolation(
            XssViolationException ex, HttpServletRequest request) {
        
        // 1. 장기 IP 차단 (XSS는 매우 심각한 위협)
        String clientIp = getClientIpAddress(request);
        if (autoBlocking) {
            int blockDuration = ex.getSeverity().equals("HIGH") ? 1800 : 900; // 30분 또는 15분
            monitoringService.applySecurityPenalty(clientIp, "XSS_ATTACK", blockDuration);
        }
        
        // 2. 공격 패턴 학습 및 기록
        logSecurityException(ex, request);
        monitoringService.recordXssAttack(ex, request);
        monitoringService.learnAttackPattern(ex.getDetectedPattern());
        
        // 3. 심각한 XSS 공격 시 관리자 알림
        if (ex.getSeverity().equals("CRITICAL")) {
            monitoringService.sendCriticalSecurityAlert("CRITICAL XSS 공격", ex, request);
        }
        
        // 4. 에러 응답 생성
        ErrorResponse errorResponse = createSecurityErrorResponse(ex, request);
        
        // 5. 악성 입력 내용 완전 마스킹
        if (maskSensitiveData) {
            errorResponse = maskXssContent(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            "허용되지 않은 문자나 태그가 포함되어 있습니다", 
            ex.getErrorCode().getCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }
    
    /**
     * Rate Limiting 위반 예외 처리
     */
    @ExceptionHandler(RateLimitViolationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleRateLimitViolation(
            RateLimitViolationException ex, HttpServletRequest request) {
        
        // 1. 연속 위반 시 추가 페널티
        String clientIp = getClientIpAddress(request);
        if (autoBlocking && ex.getViolationCount() > 3) {
            monitoringService.applySecurityPenalty(clientIp, "RATE_LIMIT_ABUSE", 1200); // 20분
        }
        
        // 2. Rate Limiting 이벤트 기록
        logSecurityException(ex, request);
        monitoringService.recordRateLimitViolation(ex, request);
        
        // 3. 에러 응답 생성
        ErrorResponse errorResponse = createSecurityErrorResponse(ex, request);
        errorResponse.setRetryAfter(ex.getRetryAfterSeconds());
        errorResponse.setRateLimitInfo(Map.of(
            "limit", ex.getLimit(),
            "remaining", 0,
            "resetTime", ex.getResetTime(),
            "limitType", ex.getLimitType()
        ));
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            String.format("요청 한도를 초과했습니다. %d초 후 다시 시도해주세요", ex.getRetryAfterSeconds()), 
            ex.getErrorCode().getCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .header("X-RateLimit-Limit", String.valueOf(ex.getLimit()))
            .header("X-RateLimit-Remaining", "0")
            .header("X-RateLimit-Reset", String.valueOf(ex.getResetTime()))
            .body(response);
    }
    
    /**
     * SQL Injection 공격 예외 처리
     */
    @ExceptionHandler(SqlInjectionException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleSqlInjection(
            SqlInjectionException ex, HttpServletRequest request) {
        
        // 1. 즉시 장기 차단 (SQL Injection은 최고 수준 위협)
        String clientIp = getClientIpAddress(request);
        if (autoBlocking) {
            monitoringService.applySecurityPenalty(clientIp, "SQL_INJECTION", 3600); // 1시간
        }
        
        // 2. 긴급 보안 알림
        logCriticalSecurityException(ex, request);
        monitoringService.recordSqlInjection(ex, request);
        monitoringService.sendCriticalSecurityAlert("SQL Injection 공격 탐지", ex, request);
        
        // 3. 공격 패턴 분석 및 학습
        monitoringService.analyzeSqlInjectionPattern(ex.getDetectedPattern());
        
        // 4. 에러 응답 생성 (최소 정보만 제공)
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode(ex.getErrorCode().getCode())
            .userMessage("잘못된 요청입니다")
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .traceId(generateTraceId())
            .securityLevel("CRITICAL")
            .build();
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            "잘못된 요청입니다", 
            ex.getErrorCode().getCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }
    
    /**
     * 보안 검증 실패 예외 처리
     */
    @ExceptionHandler(SecurityValidationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleSecurityValidation(
            SecurityValidationException ex, HttpServletRequest request) {
        
        // 1. 위협 수준별 차등 대응
        String clientIp = getClientIpAddress(request);
        if (autoBlocking && ex.getThreatLevel() != SecurityValidationException.ThreatLevel.LOW) {
            int blockDuration = calculateBlockDuration(ex.getThreatLevel());
            monitoringService.applySecurityPenalty(clientIp, "SECURITY_VALIDATION", blockDuration);
        }
        
        // 2. 보안 검증 이벤트 기록
        logSecurityException(ex, request);
        monitoringService.recordSecurityValidation(ex, request);
        
        // 3. 에러 응답 생성
        ErrorResponse errorResponse = createSecurityErrorResponse(ex, request);
        
        // 4. 입력값 마스킹
        if (maskSensitiveData) {
            errorResponse = maskInputValue(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            "입력 내용에 허용되지 않는 문자가 포함되어 있습니다", 
            ex.getErrorCode().getCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(response);
    }
    
    // ========== 보조 메서드 ==========
    
    /**
     * 보안 에러 응답 생성
     */
    private ErrorResponse createSecurityErrorResponse(BaseException ex, HttpServletRequest request) {
        return ErrorResponse.builder()
            .errorCode(ex.getErrorCode().getCode())
            .userMessage(ex.getUserMessage())
            .developerMessage(ex.getDeveloperMessage())
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .traceId(generateTraceId())
            .ipAddress(maskSensitiveData ? maskIpAddress(getClientIpAddress(request)) : getClientIpAddress(request))
            .userAgent(request.getHeader("User-Agent"))
            .securityLevel("HIGH")
            .build();
    }
    
    /**
     * 보안 예외 로깅
     */
    private void logSecurityException(BaseException ex, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        
        log.warn("Security exception occurred: {} | IP: {} | UserAgent: {} | Path: {} | Message: {}", 
            ex.getClass().getSimpleName(),
            maskIpAddress(clientIp),
            userAgent,
            request.getRequestURI(),
            ex.getUserMessage()
        );
    }
    
    /**
     * 심각한 보안 예외 로깅
     */
    private void logCriticalSecurityException(BaseException ex, HttpServletRequest request) {
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        
        log.error("CRITICAL security exception occurred: {} | IP: {} | UserAgent: {} | Path: {} | Message: {}", 
            ex.getClass().getSimpleName(),
            maskIpAddress(clientIp),
            userAgent,
            request.getRequestURI(),
            ex.getUserMessage()
        );
    }
    
    /**
     * 위협 수준별 차단 시간 계산
     */
    private int calculateBlockDuration(SecurityValidationException.ThreatLevel threatLevel) {
        switch (threatLevel) {
            case LOW: return 0;           // 차단 없음
            case MEDIUM: return 300;      // 5분
            case HIGH: return 900;        // 15분  
            case CRITICAL: return 3600;   // 1시간
            default: return 300;
        }
    }
    
    // ========== 마스킹 메서드 ==========
    
    /**
     * Origin 정보 마스킹
     */
    private ErrorResponse maskOriginInfo(ErrorResponse errorResponse, CorsViolationException ex) {
        String maskedOrigin = sensitiveDataMasker.maskDomain(ex.getRequestOrigin());
        errorResponse.setSecurityDetails(Map.of(
            "violationType", "CORS_VIOLATION",
            "requestOrigin", maskedOrigin,
            "allowedOrigins", "[MASKED]"
        ));
        return errorResponse;
    }
    
    /**
     * CSRF 토큰 정보 마스킹
     */
    private ErrorResponse maskCsrfTokenInfo(ErrorResponse errorResponse, CsrfViolationException ex) {
        errorResponse.setSecurityDetails(Map.of(
            "violationType", "CSRF_VIOLATION",
            "expectedToken", "[MASKED]",
            "receivedToken", "[MASKED]",
            "sessionValid", ex.isSessionValid()
        ));
        return errorResponse;
    }
    
    /**
     * XSS 공격 내용 마스킹
     */
    private ErrorResponse maskXssContent(ErrorResponse errorResponse, XssViolationException ex) {
        errorResponse.setSecurityDetails(Map.of(
            "violationType", "XSS_ATTACK",
            "detectedPattern", "[MASKED_XSS_PATTERN]",
            "severity", ex.getSeverity(),
            "fieldName", ex.getFieldName()
        ));
        return errorResponse;
    }
    
    /**
     * 입력값 마스킹
     */
    private ErrorResponse maskInputValue(ErrorResponse errorResponse, SecurityValidationException ex) {
        errorResponse.setSecurityDetails(Map.of(
            "violationType", "SECURITY_VALIDATION",
            "fieldName", ex.getFieldName(),
            "inputValue", "[MASKED]",
            "threatLevel", ex.getThreatLevel().name()
        ));
        return errorResponse;
    }
    
    /**
     * IP 주소 마스킹
     */
    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return "N/A";
        }
        String[] parts = ipAddress.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***." + "***";
        }
        return "***.***.***." + "***";
    }
    
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
```

## 🔧 SecurityExceptionHandler 핵심 기능

### 1. CORS 위반 처리
- **의심 활동 탐지**: 반복적인 CORS 위반 시 자동 차단
- **차단 시간**: 10분 IP 차단
- **Origin 마스킹**: 요청 출처 정보 보안 처리

### 2. CSRF 공격 처리
- **토큰 검증 실패**: 15분 IP 차단
- **보안 알림**: 관리자에게 즉시 알림 발송
- **토큰 정보 마스킹**: 모든 토큰 정보 완전 마스킹

### 3. XSS 공격 처리
- **패턴 학습**: 공격 패턴 분석 및 학습
- **차등 차단**: HIGH(30분), CRITICAL(즉시 알림)
- **악성 내용 마스킹**: 스크립트 태그 완전 제거

### 4. Rate Limiting 위반
- **연속 위반**: 3회 초과 시 20분 추가 차단
- **재시도 안내**: Retry-After 헤더로 대기 시간 명시
- **제한 정보**: 현재 제한/남은 요청 수 제공

### 5. SQL Injection 처리
- **최고 수준 위협**: 1시간 즉시 차단
- **긴급 알림**: 관리자에게 CRITICAL 레벨 알림
- **최소 정보**: 공격자에게 최소한의 정보만 제공

---

*step8-4a1 완료: SecurityExceptionHandler 보안 예외 전담 처리*  
*다음: step8-4a2_integrated_exception_handler.md (통합 글로벌 예외 처리기)*