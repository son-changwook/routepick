# Step 8-4a2: 비즈니스 예외 처리기 및 통합

> 도메인별 비즈니스 예외 처리 및 Spring 표준 예외 통합  
> 생성일: 2025-08-21  
> 단계: 8-4a2 (비즈니스 예외 처리 통합)  
> 참고: step3-3a, step8-4a1

---

## 🏗️ IntegratedGlobalExceptionHandler 구현

### 통합 예외 처리기 (비즈니스 예외 담당)
```java
package com.routepick.exception.handler;

import com.routepick.common.ApiResponse;
import com.routepick.exception.dto.ErrorResponse;
import com.routepick.exception.dto.ValidationErrorResponse;
import com.routepick.exception.dto.FieldErrorDetail;
import com.routepick.exception.auth.*;
import com.routepick.exception.user.*;
import com.routepick.exception.gym.*;
import com.routepick.exception.route.*;
import com.routepick.exception.tag.*;
import com.routepick.exception.payment.*;
import com.routepick.exception.validation.*;
import com.routepick.exception.system.*;
import com.routepick.monitoring.SecurityMonitoringService;
import com.routepick.security.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 통합 글로벌 예외 처리기 (비즈니스 예외 담당)
 * 
 * 처리 예외:
 * - 8개 도메인 비즈니스 예외
 * - Spring 표준 예외
 * - 한국어 메시지 체계
 * - 민감정보 마스킹
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class IntegratedGlobalExceptionHandler {
    
    private final SecurityMonitoringService monitoringService;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final MessageSource messageSource;
    
    @Value("${app.security.mask-sensitive-data:true}")
    private boolean maskSensitiveData;
    
    @Value("${app.security.detailed-error-info:false}")
    private boolean detailedErrorInfo;

    // ========== 도메인별 비즈니스 예외 처리 ==========

    /**
     * 인증 관련 예외 처리
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleAuthException(
            AuthException ex, HttpServletRequest request) {
        
        logBusinessException(ex, request);
        monitoringService.recordAuthException(ex, request);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // 민감한 인증 정보 마스킹
        if (maskSensitiveData) {
            errorResponse = sanitizeAuthError(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            getKoreanMessage(ex.getErrorCode(), ex.getUserMessage()), 
            ex.getErrorCode().getCode()
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
        monitoringService.recordUserException(ex, request);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // 개인정보 마스킹
        if (maskSensitiveData) {
            errorResponse = sanitizeUserError(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            getKoreanMessage(ex.getErrorCode(), ex.getUserMessage()), 
            ex.getErrorCode().getCode()
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
        monitoringService.recordGymException(ex, request);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // GPS 좌표 등 위치 정보 마스킹
        if (maskSensitiveData) {
            errorResponse = sanitizeGymError(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            getKoreanMessage(ex.getErrorCode(), ex.getUserMessage()), 
            ex.getErrorCode().getCode()
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
        monitoringService.recordRouteException(ex, request);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            getKoreanMessage(ex.getErrorCode(), ex.getUserMessage()), 
            ex.getErrorCode().getCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }

    /**
     * 태그 관련 예외 처리
     */
    @ExceptionHandler(TagException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleTagException(
            TagException ex, HttpServletRequest request) {
        
        logBusinessException(ex, request);
        monitoringService.recordTagException(ex, request);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            getKoreanMessage(ex.getErrorCode(), ex.getUserMessage()), 
            ex.getErrorCode().getCode()
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
        monitoringService.recordPaymentException(ex, request);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // 결제 정보 마스킹 (카드번호, 계좌번호 등)
        if (maskSensitiveData) {
            errorResponse = sanitizePaymentError(errorResponse, ex);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            getKoreanMessage(ex.getErrorCode(), ex.getUserMessage()), 
            ex.getErrorCode().getCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }

    /**
     * 검증 관련 예외 처리
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        
        logValidationException(ex, request);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            getKoreanMessage(ex.getErrorCode(), ex.getUserMessage()), 
            ex.getErrorCode().getCode()
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
        monitoringService.recordSystemException(ex, request);
        
        ErrorResponse errorResponse = createErrorResponse(ex, request);
        
        // 시스템 내부 정보 제한적 노출
        if (!detailedErrorInfo) {
            errorResponse = sanitizeSystemError(errorResponse);
        }
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            getKoreanMessage(ex.getErrorCode(), ex.getUserMessage()), 
            ex.getErrorCode().getCode()
        );
        response.setData(errorResponse);
        
        return ResponseEntity
            .status(ex.getErrorCode().getHttpStatus())
            .body(response);
    }
    
    // ========== Spring 표준 예외 처리 ==========
    
    /**
     * @Valid 검증 실패 예외 처리 (한국어 필드 에러)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ValidationErrorResponse>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        logValidationException(ex, request);
        
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::createKoreanFieldErrorDetail)
            .collect(Collectors.toList());
        
        ValidationErrorResponse errorResponse = ValidationErrorResponse.builder()
            .errorCode("VALIDATION-001")
            .userMessage("입력 정보를 확인해주세요")
            .developerMessage("Validation failed for request body")
            .fieldErrors(fieldErrors)
            .totalErrors(fieldErrors.size())
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .traceId(generateTraceId())
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
     * 메서드 파라미터 타입 불일치 예외 처리
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<ErrorResponse>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        logValidationException(ex, request);
        
        String fieldName = getKoreanFieldName(ex.getName());
        String expectedType = getKoreanTypeName(ex.getRequiredType());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode("VALIDATION-002")
            .userMessage(String.format("%s의 형식이 올바르지 않습니다. %s 형태로 입력해주세요", 
                                     fieldName, expectedType))
            .developerMessage("Type mismatch for parameter: " + ex.getName())
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .traceId(generateTraceId())
            .build();
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            errorResponse.getUserMessage(), 
            "VALIDATION-002"
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
        monitoringService.recordAccessDenied(ex, request);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode("AUTH-041")
            .userMessage("접근 권한이 없습니다")
            .developerMessage("Access denied to requested resource")
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .traceId(generateTraceId())
            .securityLevel("MEDIUM")
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
            .traceId(generateTraceId())
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
            .userMessage("요청한 페이지를 찾을 수 없습니다")
            .developerMessage("No handler found for " + ex.getHttpMethod() + " " + ex.getRequestURL())
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .traceId(generateTraceId())
            .build();
        
        ApiResponse<ErrorResponse> response = ApiResponse.error(
            "요청한 페이지를 찾을 수 없습니다", 
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
        monitoringService.recordUnhandledException(ex, request);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .errorCode("SYSTEM-001")
            .userMessage("일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요")
            .developerMessage(detailedErrorInfo ? ex.getMessage() : "Internal server error occurred")
            .timestamp(LocalDateTime.now())
            .path(request.getRequestURI())
            .traceId(generateTraceId())
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
            .ipAddress(getClientIpAddress(request))
            .userAgent(request.getHeader("User-Agent"))
            .build();
    }
    
    /**
     * 한국어 필드 에러 상세 정보 생성
     */
    private FieldErrorDetail createKoreanFieldErrorDetail(FieldError fieldError) {
        // 한국어 필드명 매핑
        String koreanFieldName = getKoreanFieldName(fieldError.getField());
        String koreanMessage = getKoreanValidationMessage(fieldError);
        
        return FieldErrorDetail.builder()
            .field(fieldError.getField())
            .fieldName(koreanFieldName)
            .rejectedValue(maskSensitiveData ? 
                sensitiveDataMasker.mask(String.valueOf(fieldError.getRejectedValue())) :
                String.valueOf(fieldError.getRejectedValue()))
            .message(koreanMessage)
            .code(fieldError.getCode())
            .expectedFormat(getExpectedFormat(fieldError.getField()))
            .suggestions(getFieldSuggestions(fieldError.getField()))
            .build();
    }
    
    /**
     * 한국어 메시지 조회
     */
    private String getKoreanMessage(ErrorCode errorCode, String defaultMessage) {
        try {
            return messageSource.getMessage(
                "error." + errorCode.getCode().toLowerCase(), 
                null, 
                defaultMessage, 
                Locale.KOREA
            );
        } catch (Exception e) {
            return defaultMessage;
        }
    }
    
    /**
     * 한국어 필드명 매핑
     */
    private String getKoreanFieldName(String fieldName) {
        Map<String, String> fieldNameMap = Map.of(
            "email", "이메일",
            "password", "비밀번호",
            "nickName", "닉네임",
            "phoneNumber", "휴대폰 번호",
            "birthDate", "생년월일",
            "gymName", "체육관 이름",
            "routeName", "루트 이름",
            "address", "주소"
        );
        
        return fieldNameMap.getOrDefault(fieldName, fieldName);
    }
    
    /**
     * 한국어 타입명 매핑
     */
    private String getKoreanTypeName(Class<?> type) {
        if (type == null) return "올바른 값";
        
        Map<String, String> typeNameMap = Map.of(
            "Long", "숫자",
            "Integer", "정수",
            "Boolean", "true/false",
            "LocalDateTime", "날짜시간 (YYYY-MM-DD HH:MM:SS)",
            "LocalDate", "날짜 (YYYY-MM-DD)",
            "Double", "소수"
        );
        
        return typeNameMap.getOrDefault(type.getSimpleName(), type.getSimpleName());
    }
    
    /**
     * 한국어 검증 메시지
     */
    private String getKoreanValidationMessage(FieldError fieldError) {
        String code = fieldError.getCode();
        String field = getKoreanFieldName(fieldError.getField());
        
        Map<String, String> messageMap = Map.of(
            "NotNull", field + "은(는) 필수 입력 항목입니다",
            "NotEmpty", field + "을(를) 입력해주세요",
            "NotBlank", field + "을(를) 입력해주세요",
            "Size", field + "의 길이가 올바르지 않습니다",
            "Email", "올바른 이메일 주소를 입력해주세요",
            "Pattern", field + "의 형식이 올바르지 않습니다"
        );
        
        return messageMap.getOrDefault(code, fieldError.getDefaultMessage());
    }
    
    /**
     * 기대 형식 제공
     */
    private String getExpectedFormat(String fieldName) {
        Map<String, String> formatMap = Map.of(
            "email", "example@domain.com",
            "phoneNumber", "010-1234-5678",
            "password", "8자 이상, 영문/숫자/특수문자 조합",
            "birthDate", "YYYY-MM-DD",
            "nickName", "2-10자 한글/영문/숫자"
        );
        
        return formatMap.get(fieldName);
    }
    
    /**
     * 필드별 수정 제안사항
     */
    private List<String> getFieldSuggestions(String fieldName) {
        Map<String, List<String>> suggestionMap = Map.of(
            "email", Arrays.asList("@가 포함되어야 합니다", "유효한 도메인을 사용하세요"),
            "phoneNumber", Arrays.asList("010-1234-5678 형식으로 입력하세요", "하이픈(-)을 포함해주세요"),
            "password", Arrays.asList("최소 8자 이상 입력하세요", "영문, 숫자, 특수문자를 모두 포함하세요"),
            "nickName", Arrays.asList("2-10자 사이로 입력하세요", "특수문자는 사용할 수 없습니다")
        );
        
        return suggestionMap.getOrDefault(fieldName, new ArrayList<>());
    }
    
    // ========== 마스킹 메서드 ==========
    
    private ErrorResponse sanitizeAuthError(ErrorResponse errorResponse, AuthException ex) {
        return ErrorResponse.builder()
            .errorCode(errorResponse.getErrorCode())
            .userMessage("인증 정보를 확인해주세요")
            .developerMessage("Authentication failed")
            .timestamp(errorResponse.getTimestamp())
            .path(errorResponse.getPath())
            .traceId(errorResponse.getTraceId())
            .securityLevel("HIGH")
            .build();
    }

    private ErrorResponse sanitizeUserError(ErrorResponse errorResponse, UserException ex) {
        return sensitiveDataMasker.maskUserErrorResponse(errorResponse, ex);
    }

    private ErrorResponse sanitizeGymError(ErrorResponse errorResponse, GymException ex) {
        return sensitiveDataMasker.maskGymErrorResponse(errorResponse, ex);
    }

    private ErrorResponse sanitizePaymentError(ErrorResponse errorResponse, PaymentException ex) {
        return sensitiveDataMasker.maskPaymentErrorResponse(errorResponse, ex);
    }

    private ErrorResponse sanitizeSystemError(ErrorResponse errorResponse) {
        return ErrorResponse.builder()
            .errorCode(errorResponse.getErrorCode())
            .userMessage("시스템 오류가 발생했습니다")
            .developerMessage("Internal system error")
            .timestamp(errorResponse.getTimestamp())
            .path(errorResponse.getPath())
            .traceId(errorResponse.getTraceId())
            .build();
    }
    
    // ========== 로깅 메서드 ==========
    
    private void logBusinessException(BaseException ex, HttpServletRequest request) {
        log.warn("📋 비즈니스 예외 발생 - Code: {}, Message: {}, URI: {}", 
                ex.getErrorCode().getCode(), ex.getMessage(), request.getRequestURI());
    }
    
    private void logValidationException(Exception ex, HttpServletRequest request) {
        log.info("✅ 검증 실패 - Exception: {}, URI: {}", 
                ex.getClass().getSimpleName(), request.getRequestURI());
    }
    
    private void logSecurityException(Exception ex, HttpServletRequest request) {
        log.warn("🔐 보안 예외 - Exception: {}, URI: {}, IP: {}", 
                ex.getClass().getSimpleName(), request.getRequestURI(), getClientIpAddress(request));
    }
    
    private void logSystemException(Exception ex, HttpServletRequest request) {
        log.error("🚨 시스템 예외 - Exception: {}, Message: {}, URI: {}", 
                ex.getClass().getSimpleName(), ex.getMessage(), request.getRequestURI(), ex);
    }
    
    // ========== 유틸리티 메서드 ==========
    
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

---

## 📋 한국어 메시지 파일

### messages/error-messages_ko.properties
```properties
# 인증 예외
error.auth-001=이메일 또는 비밀번호가 올바르지 않습니다
error.auth-002=계정이 잠겨있습니다. 관리자에게 문의하세요
error.auth-003=토큰이 만료되었습니다. 다시 로그인해주세요
error.auth-004=권한이 없습니다

# 사용자 예외  
error.user-001=존재하지 않는 사용자입니다
error.user-002=이미 사용중인 이메일입니다
error.user-003=이미 사용중인 닉네임입니다
error.user-004=휴대폰 번호 형식이 올바르지 않습니다

# 체육관 예외
error.gym-001=존재하지 않는 체육관입니다
error.gym-002=체육관 위치 정보가 올바르지 않습니다
error.gym-003=체육관 영업시간을 확인해주세요

# 루트 예외
error.route-001=존재하지 않는 루트입니다
error.route-002=루트 난이도가 올바르지 않습니다
error.route-003=루트 이미지 업로드에 실패했습니다

# 태그 예외
error.tag-001=존재하지 않는 태그입니다
error.tag-002=사용자당 최대 {0}개까지만 태그를 선택할 수 있습니다

# 결제 예외
error.payment-001=결제 정보가 올바르지 않습니다
error.payment-002=결제가 취소되었습니다
error.payment-003=환불 처리 중 오류가 발생했습니다

# 검증 예외
error.validation-001=입력 정보를 확인해주세요
error.validation-002=형식이 올바르지 않습니다

# 시스템 예외
error.system-001=일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요
```

---

## 🔧 Spring Boot 통합 설정

### application.yml 메시지 설정
```yaml
spring:
  messages:
    basename: messages/error-messages
    encoding: UTF-8
    cache-duration: 3600
    fallback-to-system-locale: false
    
# 예외 처리 설정
app:
  security:
    mask-sensitive-data: true
    detailed-error-info: false
  
  exception:
    korean-messages: true
    field-suggestions: true
    trace-logging: true
```

---

## 📊 주요 기능

### **1. 8개 도메인 예외 처리**
- **AUTH**: 인증/인가 실패, 토큰 만료, 권한 부족
- **USER**: 사용자 조회 실패, 중복 검증, 개인정보 오류
- **GYM**: 체육관 정보 오류, GPS 좌표 검증 실패
- **ROUTE**: 루트 정보 오류, 미디어 업로드 실패
- **TAG**: 태그 관리, 개수 제한 초과
- **PAYMENT**: 결제 실패, 환불 처리 오류
- **VALIDATION**: 입력 검증 실패
- **SYSTEM**: 시스템 내부 오류

### **2. 한국어 사용자 경험**
- **필드명**: email → "이메일", phoneNumber → "휴대폰 번호"
- **검증 메시지**: @NotNull → "필수 입력 항목입니다"
- **형식 가이드**: "010-1234-5678", "YYYY-MM-DD" 등
- **수정 제안**: 구체적인 입력 가이드 제공

### **3. Spring 표준 예외 통합**
- **@Valid 검증 실패**: 한국어 필드 에러 상세 제공
- **타입 불일치**: 기대 타입 한국어 안내  
- **Access Denied**: 권한 부족 안내
- **Method Not Allowed**: 허용 메서드 안내
- **Not Found**: 404 에러 친화적 메시지

---

## 🚀 **다음 단계**

**step8-4b 연계:**
- Security Monitoring System 통합
- 실시간 보안 이벤트 추적
- 알림 시스템 연동

*step8-4a2 완성: 비즈니스 예외 처리기 및 통합 완료*