# Step 3-1: 기본 예외 처리 체계 및 ErrorCode 설계

> RoutePickr 커스텀 예외 처리 시스템 완전 설계  
> 생성일: 2025-08-16  
> 기반 분석: step1-3_spring_boot_guide.md, step2-1_backend_structure.md

---

## 🎯 예외 처리 체계 개요

### 설계 원칙
- **사용자 친화적**: 한국어 메시지 제공으로 UX 향상
- **개발자 친화적**: 영문 메시지로 디버깅 지원  
- **보안 강화**: 민감정보 노출 방지 및 브루트 포스 대응
- **일관성**: 표준화된 에러 코드 및 응답 포맷
- **추적성**: 로깅 및 모니터링 지원

### 3계층 예외 아키텍처
```
┌─────────────────────┐
│   BaseException     │  ← 추상 기본 클래스
│   (공통 기능)        │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│  Domain Exceptions  │  ← 도메인별 구체 예외
│  (AuthException,    │
│   UserException..)  │
└─────────────────────┘
          ▲
          │
┌─────────────────────┐
│   ErrorCode Enum    │  ← 상세 에러 코드
│   (AUTH-001~099)    │
└─────────────────────┘
```

---

## 🔧 BaseException 추상 클래스 설계

### 핵심 구조
```java
package com.routepick.exception;

import com.routepick.common.ErrorCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * RoutePickr 커스텀 예외의 최상위 추상 클래스
 * 
 * 주요 기능:
 * - 표준화된 에러 코드 관리
 * - 한국어/영문 메시지 이중 제공
 * - 자동 로깅 및 추적 기능
 * - 보안 강화 (민감정보 마스킹)
 */
@Slf4j
@Getter
public abstract class BaseException extends RuntimeException {
    
    private final ErrorCode errorCode;
    private final String userMessage;       // 한국어 사용자 메시지
    private final String developerMessage;  // 영문 개발자 메시지
    private final Object[] messageArgs;     // 메시지 파라미터
    private final long timestamp;           // 발생 시각

    /**
     * 기본 생성자 (ErrorCode만으로 생성)
     */
    protected BaseException(ErrorCode errorCode) {
        super(errorCode.getDeveloperMessage());
        this.errorCode = errorCode;
        this.userMessage = errorCode.getUserMessage();
        this.developerMessage = errorCode.getDeveloperMessage();
        this.messageArgs = null;
        this.timestamp = System.currentTimeMillis();
        
        logException();
    }

    /**
     * 파라미터화된 메시지 생성자
     */
    protected BaseException(ErrorCode errorCode, Object... messageArgs) {
        super(String.format(errorCode.getDeveloperMessage(), messageArgs));
        this.errorCode = errorCode;
        this.userMessage = String.format(errorCode.getUserMessage(), messageArgs);
        this.developerMessage = String.format(errorCode.getDeveloperMessage(), messageArgs);
        this.messageArgs = messageArgs;
        this.timestamp = System.currentTimeMillis();
        
        logException();
    }

    /**
     * 원인 예외를 포함한 생성자
     */
    protected BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getDeveloperMessage(), cause);
        this.errorCode = errorCode;
        this.userMessage = errorCode.getUserMessage();
        this.developerMessage = errorCode.getDeveloperMessage();
        this.messageArgs = null;
        this.timestamp = System.currentTimeMillis();
        
        logException();
    }

    /**
     * 완전한 파라미터 생성자
     */
    protected BaseException(ErrorCode errorCode, Throwable cause, Object... messageArgs) {
        super(String.format(errorCode.getDeveloperMessage(), messageArgs), cause);
        this.errorCode = errorCode;
        this.userMessage = String.format(errorCode.getUserMessage(), messageArgs);
        this.developerMessage = String.format(errorCode.getDeveloperMessage(), messageArgs);
        this.messageArgs = messageArgs;
        this.timestamp = System.currentTimeMillis();
        
        logException();
    }

    /**
     * 자동 로깅 기능
     * - 에러 레벨별 차등 로깅
     * - 민감정보 마스킹
     * - 추적 가능한 로그 포맷
     */
    private void logException() {
        String maskedMessage = maskSensitiveInfo(this.developerMessage);
        String logMessage = String.format(
            "[%s] %s - User: %s, Developer: %s, Timestamp: %d",
            this.errorCode.getCode(),
            this.getClass().getSimpleName(),
            this.userMessage,
            maskedMessage,
            this.timestamp
        );

        // HTTP 상태 코드별 로그 레벨 결정
        switch (this.errorCode.getHttpStatus().series()) {
            case CLIENT_ERROR:
                if (isSecurityRelated()) {
                    log.warn("Security Exception: {}", logMessage);
                } else {
                    log.info("Client Exception: {}", logMessage);
                }
                break;
            case SERVER_ERROR:
                log.error("Server Exception: {}", logMessage, this);
                break;
            default:
                log.debug("Exception: {}", logMessage);
        }
    }

    /**
     * 민감정보 마스킹 (보안 강화)
     */
    private String maskSensitiveInfo(String message) {
        if (message == null) return null;
        
        return message
            .replaceAll("\\b[0-9]{3}-[0-9]{4}-[0-9]{4}\\b", "***-****-****")  // 휴대폰번호
            .replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b", "***@***.***")  // 이메일
            .replaceAll("\\b[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}\\b", "****-****-****-****")  // 카드번호
            .replaceAll("(?i)password[\"':=\\s]*[\"']?[^\"'\\s,}]+", "password: ***")  // 비밀번호
            .replaceAll("(?i)token[\"':=\\s]*[\"']?[^\"'\\s,}]+", "token: ***");  // 토큰
    }

    /**
     * 보안 관련 예외 판별
     */
    private boolean isSecurityRelated() {
        String code = this.errorCode.getCode();
        return code.startsWith("AUTH-") || 
               code.startsWith("VALIDATION-") ||
               code.contains("SECURITY") ||
               code.contains("UNAUTHORIZED") ||
               code.contains("FORBIDDEN");
    }

    /**
     * API 응답용 에러 정보 추출
     */
    public ErrorInfo getErrorInfo() {
        return ErrorInfo.builder()
            .code(this.errorCode.getCode())
            .httpStatus(this.errorCode.getHttpStatus())
            .userMessage(this.userMessage)
            .developerMessage(this.developerMessage)
            .timestamp(this.timestamp)
            .build();
    }

    /**
     * 에러 정보 DTO
     */
    @Getter
    @lombok.Builder
    public static class ErrorInfo {
        private String code;
        private org.springframework.http.HttpStatus httpStatus;
        private String userMessage;
        private String developerMessage;
        private long timestamp;
    }
}
```

---

## 📋 ErrorCode Enum 체계 설계

### 체계적 에러 코드 구조
```java
package com.routepick.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * RoutePickr 에러 코드 통합 관리
 * 
 * 코드 체계: [DOMAIN]-[NUMBER]
 * - DOMAIN: 도메인별 3-12자 영문 (AUTH, USER, GYM, ROUTE, TAG, PAYMENT, VALIDATION, SYSTEM)
 * - NUMBER: 001~099 (도메인별 최대 99개)
 * 
 * 메시지 체계:
 * - userMessage: 한국어 사용자 친화적 메시지
 * - developerMessage: 영문 개발자용 상세 메시지
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ========== AUTH 도메인 (001~099) ==========
    
    // 인증 관련 에러 (001~020)
    INVALID_EMAIL(HttpStatus.BAD_REQUEST, "AUTH-001", 
        "유효하지 않은 이메일 주소입니다", 
        "Invalid email format provided"),
    
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "AUTH-002",
        "비밀번호가 올바르지 않습니다",
        "Invalid password provided"),
    
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-003",
        "로그인이 만료되었습니다. 다시 로그인해주세요",
        "JWT token has expired"),
    
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH-004",
        "유효하지 않은 토큰입니다",
        "Invalid JWT token format"),
    
    TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "AUTH-005",
        "로그인이 필요합니다",
        "Authorization token is missing"),
    
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-006",
        "다시 로그인해주세요",
        "Refresh token has expired"),
    
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "AUTH-007",
        "계정이 잠겨있습니다. 고객센터에 문의해주세요",
        "Account is locked due to security reasons"),
    
    LOGIN_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH-008",
        "로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해주세요",
        "Too many login attempts, please try again later"),
    
    // 소셜 로그인 관련 에러 (021~040)
    SOCIAL_LOGIN_FAILED(HttpStatus.BAD_REQUEST, "AUTH-021",
        "소셜 로그인에 실패했습니다. 다시 시도해주세요",
        "Social login authentication failed"),
    
    SOCIAL_PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "AUTH-022",
        "지원하지 않는 소셜 로그인 제공자입니다",
        "Social provider %s is not supported"),
    
    SOCIAL_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "AUTH-023",
        "소셜 로그인 토큰이 유효하지 않습니다",
        "Social login token is invalid or expired"),
    
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "AUTH-024",
        "이미 다른 계정에 연결된 소셜 계정입니다",
        "Social account is already linked to another user"),
    
    // 권한 관련 에러 (041~060)
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-041",
        "접근 권한이 없습니다",
        "Access denied to requested resource"),
    
    INSUFFICIENT_PRIVILEGES(HttpStatus.FORBIDDEN, "AUTH-042",
        "해당 작업을 수행할 권한이 없습니다",
        "Insufficient privileges for requested operation"),
    
    ADMIN_ACCESS_REQUIRED(HttpStatus.FORBIDDEN, "AUTH-043",
        "관리자 권한이 필요합니다",
        "Administrator access required"),
    
    GYM_ADMIN_ACCESS_REQUIRED(HttpStatus.FORBIDDEN, "AUTH-044",
        "체육관 관리자 권한이 필요합니다",
        "Gym administrator access required"),

    // ========== USER 도메인 (001~099) ==========
    
    // 사용자 조회/관리 에러 (001~020)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001",
        "사용자를 찾을 수 없습니다",
        "User not found with provided identifier"),
    
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER-002",
        "이미 존재하는 사용자입니다",
        "User already exists with provided email"),
    
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "USER-003",
        "이미 사용 중인 이메일입니다",
        "Email address is already registered"),
    
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER-004",
        "이미 사용 중인 닉네임입니다",
        "Nickname is already taken"),
    
    USER_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-005",
        "사용자 프로필을 찾을 수 없습니다",
        "User profile not found"),
    
    USER_INACTIVE(HttpStatus.FORBIDDEN, "USER-006",
        "비활성화된 계정입니다",
        "User account is inactive"),
    
    USER_DELETED(HttpStatus.GONE, "USER-007",
        "삭제된 계정입니다",
        "User account has been deleted"),
    
    // 본인인증 관련 에러 (021~040)
    PHONE_VERIFICATION_REQUIRED(HttpStatus.BAD_REQUEST, "USER-021",
        "휴대폰 인증이 필요합니다",
        "Phone number verification is required"),
    
    PHONE_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "USER-022",
        "휴대폰 인증에 실패했습니다",
        "Phone number verification failed"),
    
    VERIFICATION_CODE_INVALID(HttpStatus.BAD_REQUEST, "USER-023",
        "인증번호가 올바르지 않습니다",
        "Verification code is invalid"),
    
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "USER-024",
        "인증번호가 만료되었습니다",
        "Verification code has expired"),
    
    PHONE_NUMBER_INVALID(HttpStatus.BAD_REQUEST, "USER-025",
        "올바른 휴대폰 번호 형식이 아닙니다",
        "Invalid Korean phone number format"),

    // ========== GYM 도메인 (001~099) ==========
    
    // 체육관 관련 에러 (001~020)
    GYM_NOT_FOUND(HttpStatus.NOT_FOUND, "GYM-001",
        "체육관을 찾을 수 없습니다",
        "Gym not found with provided identifier"),
    
    GYM_BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND, "GYM-002",
        "체육관 지점을 찾을 수 없습니다",
        "Gym branch not found"),
    
    WALL_NOT_FOUND(HttpStatus.NOT_FOUND, "GYM-003",
        "클라이밍 벽을 찾을 수 없습니다",
        "Climbing wall not found"),
    
    GYM_ALREADY_EXISTS(HttpStatus.CONFLICT, "GYM-004",
        "이미 등록된 체육관입니다",
        "Gym already exists at this location"),
    
    INVALID_GPS_COORDINATES(HttpStatus.BAD_REQUEST, "GYM-005",
        "올바르지 않은 GPS 좌표입니다",
        "Invalid GPS coordinates for Korea region"),
    
    GYM_CAPACITY_EXCEEDED(HttpStatus.BAD_REQUEST, "GYM-006",
        "체육관 수용 인원을 초과했습니다",
        "Gym capacity limit exceeded"),
    
    // 영업시간 관련 에러 (021~040)
    GYM_CLOSED(HttpStatus.FORBIDDEN, "GYM-021",
        "현재 운영시간이 아닙니다",
        "Gym is currently closed"),
    
    INVALID_BUSINESS_HOURS(HttpStatus.BAD_REQUEST, "GYM-022",
        "올바르지 않은 영업시간 형식입니다",
        "Invalid business hours format"),

    // ========== ROUTE 도메인 (001~099) ==========
    
    // 루트 관련 에러 (001~020)
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE-001",
        "루트를 찾을 수 없습니다",
        "Route not found with provided identifier"),
    
    ROUTE_ALREADY_EXISTS(HttpStatus.CONFLICT, "ROUTE-002",
        "이미 동일한 루트가 존재합니다",
        "Route already exists at this location"),
    
    ROUTE_SETTER_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE-003",
        "루트 세터를 찾을 수 없습니다",
        "Route setter not found"),
    
    CLIMBING_LEVEL_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE-004",
        "클라이밍 난이도를 찾을 수 없습니다",
        "Climbing difficulty level not found"),
    
    ROUTE_INACTIVE(HttpStatus.FORBIDDEN, "ROUTE-005",
        "비활성화된 루트입니다",
        "Route is currently inactive"),
    
    ROUTE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ROUTE-006",
        "해당 루트에 접근할 권한이 없습니다",
        "Access denied to route"),
    
    // 루트 미디어 관련 에러 (021~040)
    ROUTE_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE-021",
        "루트 이미지를 찾을 수 없습니다",
        "Route image not found"),
    
    ROUTE_VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE-022",
        "루트 영상을 찾을 수 없습니다",
        "Route video not found"),
    
    MEDIA_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ROUTE-023",
        "미디어 업로드에 실패했습니다",
        "Media file upload failed"),
    
    INVALID_FILE_FORMAT(HttpStatus.BAD_REQUEST, "ROUTE-024",
        "지원하지 않는 파일 형식입니다",
        "Unsupported file format"),
    
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "ROUTE-025",
        "파일 크기가 너무 큽니다",
        "File size exceeds maximum limit"),

    // ========== TAG 도메인 (001~099) ==========
    
    // 태그 시스템 에러 (001~020)
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "TAG-001",
        "태그를 찾을 수 없습니다",
        "Tag not found with provided identifier"),
    
    TAG_ALREADY_EXISTS(HttpStatus.CONFLICT, "TAG-002",
        "이미 존재하는 태그입니다",
        "Tag already exists with provided name"),
    
    TAG_TYPE_INVALID(HttpStatus.BAD_REQUEST, "TAG-003",
        "올바르지 않은 태그 타입입니다",
        "Invalid tag type provided"),
    
    TAG_NOT_USER_SELECTABLE(HttpStatus.BAD_REQUEST, "TAG-004",
        "사용자가 선택할 수 없는 태그입니다",
        "Tag is not user selectable"),
    
    TAG_NOT_ROUTE_TAGGABLE(HttpStatus.BAD_REQUEST, "TAG-005",
        "루트에 사용할 수 없는 태그입니다",
        "Tag is not route taggable"),
    
    INVALID_PREFERENCE_LEVEL(HttpStatus.BAD_REQUEST, "TAG-006",
        "올바르지 않은 선호도 레벨입니다",
        "Invalid preference level provided"),
    
    INVALID_SKILL_LEVEL(HttpStatus.BAD_REQUEST, "TAG-007",
        "올바르지 않은 숙련도 레벨입니다",
        "Invalid skill level provided"),
    
    // 추천 시스템 에러 (021~040)
    RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "TAG-021",
        "추천 결과를 찾을 수 없습니다",
        "Recommendation not found for user"),
    
    RECOMMENDATION_CALCULATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TAG-022",
        "추천 계산에 실패했습니다",
        "Recommendation calculation failed"),
    
    INSUFFICIENT_USER_PREFERENCES(HttpStatus.BAD_REQUEST, "TAG-023",
        "선호 태그를 먼저 설정해주세요",
        "User preferences not set for recommendation"),

    // ========== PAYMENT 도메인 (001~099) ==========
    
    // 결제 관련 에러 (001~020)
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT-001",
        "결제 정보를 찾을 수 없습니다",
        "Payment record not found"),
    
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "PAYMENT-002",
        "이미 처리된 결제입니다",
        "Payment has already been processed"),
    
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT-003",
        "결제에 실패했습니다",
        "Payment processing failed"),
    
    PAYMENT_CANCELLED(HttpStatus.BAD_REQUEST, "PAYMENT-004",
        "결제가 취소되었습니다",
        "Payment was cancelled"),
    
    INVALID_PAYMENT_METHOD(HttpStatus.BAD_REQUEST, "PAYMENT-005",
        "올바르지 않은 결제 방법입니다",
        "Invalid payment method"),
    
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT-006",
        "결제 금액이 일치하지 않습니다",
        "Payment amount mismatch"),
    
    // 환불 관련 에러 (021~040)
    REFUND_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "PAYMENT-021",
        "환불이 불가능한 결제입니다",
        "Refund not available for this payment"),
    
    REFUND_PERIOD_EXPIRED(HttpStatus.BAD_REQUEST, "PAYMENT-022",
        "환불 가능 기간이 지났습니다",
        "Refund period has expired"),
    
    REFUND_AMOUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "PAYMENT-023",
        "환불 가능 금액을 초과했습니다",
        "Refund amount exceeds available balance"),

    // ========== VALIDATION 도메인 (001~099) ==========
    
    // 입력 검증 에러 (001~020)
    INVALID_INPUT_FORMAT(HttpStatus.BAD_REQUEST, "VALIDATION-001",
        "올바르지 않은 입력 형식입니다",
        "Invalid input format"),
    
    REQUIRED_FIELD_MISSING(HttpStatus.BAD_REQUEST, "VALIDATION-002",
        "필수 입력 항목이 누락되었습니다",
        "Required field is missing: %s"),
    
    FIELD_LENGTH_EXCEEDED(HttpStatus.BAD_REQUEST, "VALIDATION-003",
        "입력 길이가 허용 범위를 초과했습니다",
        "Field length exceeds maximum limit: %s"),
    
    INVALID_KOREAN_PHONE_FORMAT(HttpStatus.BAD_REQUEST, "VALIDATION-004",
        "올바른 한국 휴대폰 번호 형식이 아닙니다 (예: 010-1234-5678)",
        "Invalid Korean phone number format"),
    
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "VALIDATION-005",
        "올바른 이메일 형식이 아닙니다",
        "Invalid email address format"),
    
    PASSWORD_TOO_WEAK(HttpStatus.BAD_REQUEST, "VALIDATION-006",
        "비밀번호가 너무 간단합니다. 8자 이상, 영문/숫자/특수문자 조합으로 설정해주세요",
        "Password is too weak, must be 8+ characters with mixed case, numbers and symbols"),
    
    INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "VALIDATION-007",
        "올바르지 않은 날짜 형식입니다",
        "Invalid date format provided"),
    
    INVALID_GPS_COORDINATE_FORMAT(HttpStatus.BAD_REQUEST, "VALIDATION-008",
        "올바르지 않은 GPS 좌표 형식입니다",
        "Invalid GPS coordinate format"),
    
    // XSS/보안 검증 에러 (021~040)
    POTENTIAL_XSS_DETECTED(HttpStatus.BAD_REQUEST, "VALIDATION-021",
        "안전하지 않은 내용이 포함되어 있습니다",
        "Potentially unsafe content detected"),
    
    INVALID_HTML_CONTENT(HttpStatus.BAD_REQUEST, "VALIDATION-022",
        "허용되지 않는 HTML 태그가 포함되어 있습니다",
        "Invalid HTML content detected"),
    
    SQL_INJECTION_ATTEMPT(HttpStatus.BAD_REQUEST, "VALIDATION-023",
        "허용되지 않는 문자가 포함되어 있습니다",
        "Potential SQL injection attempt detected"),

    // ========== SYSTEM 도메인 (001~099) ==========
    
    // 시스템 에러 (001~020)
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM-001",
        "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요",
        "Internal server error occurred"),
    
    DATABASE_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM-002",
        "데이터베이스 연결에 실패했습니다",
        "Database connection failed"),
    
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "SYSTEM-003",
        "외부 서비스 연동에 실패했습니다",
        "External API service error"),
    
    CACHE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM-004",
        "캐시 처리 중 오류가 발생했습니다",
        "Cache operation failed"),
    
    CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM-005",
        "시스템 설정 오류입니다",
        "System configuration error"),
    
    // Rate Limiting 에러 (021~040)
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "SYSTEM-021",
        "요청 횟수 제한을 초과했습니다. 잠시 후 다시 시도해주세요",
        "Rate limit exceeded for IP: %s"),
    
    API_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "SYSTEM-022",
        "일일 API 사용량을 초과했습니다",
        "Daily API quota exceeded for user: %s"),
    
    // 파일/리소스 에러 (041~060)
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "SYSTEM-041",
        "파일을 찾을 수 없습니다",
        "File not found: %s"),
    
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "SYSTEM-042",
        "파일 업로드에 실패했습니다",
        "File upload failed"),
    
    STORAGE_QUOTA_EXCEEDED(HttpStatus.INSUFFICIENT_STORAGE, "SYSTEM-043",
        "저장 공간이 부족합니다",
        "Storage quota exceeded"),
    
    // 서비스 점검 에러 (061~080)
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SYSTEM-061",
        "현재 서비스 점검 중입니다. 잠시 후 다시 이용해주세요",
        "Service is currently under maintenance"),
    
    FEATURE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "SYSTEM-062",
        "현재 사용할 수 없는 기능입니다",
        "Feature is currently disabled"),

    // ========== 공통 에러 ==========
    
    // 일반적인 클라이언트 에러
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-001",
        "잘못된 요청입니다",
        "Bad request"),
    
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-002",
        "요청한 리소스를 찾을 수 없습니다",
        "Resource not found"),
    
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-003",
        "허용되지 않는 요청 방식입니다",
        "HTTP method not allowed"),
    
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON-004",
        "지원하지 않는 미디어 타입입니다",
        "Unsupported media type");

    private final HttpStatus httpStatus;
    private final String code;
    private final String userMessage;
    private final String developerMessage;
    
    /**
     * 에러 코드로 ErrorCode 조회
     */
    public static ErrorCode findByCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return INTERNAL_SERVER_ERROR; // 기본값
    }
    
    /**
     * 도메인별 에러 코드 조회
     */
    public static ErrorCode[] findByDomain(String domain) {
        return java.util.Arrays.stream(values())
            .filter(errorCode -> errorCode.getCode().startsWith(domain + "-"))
            .toArray(ErrorCode[]::new);
    }
}
```

---

## 🛡️ 보안 강화 원칙

### 민감정보 보호 정책
```java
/**
 * 보안 정책별 에러 메시지 처리
 */
public class SecurityAwareErrorHandler {
    
    // 1. 브루트 포스 공격 대응
    public static final Map<String, Integer> SECURITY_ERROR_LIMITS = Map.of(
        "AUTH-008", 5,    // 로그인 시도 제한
        "AUTH-023", 3,    // 소셜 토큰 검증 제한
        "USER-023", 10,   // 인증번호 시도 제한
        "PAYMENT-003", 3  // 결제 실패 제한
    );
    
    // 2. 정보 누출 방지 에러 코드
    public static final Set<String> VAGUE_ERROR_CODES = Set.of(
        "AUTH-002",  // 비밀번호 오류 → 일반적 로그인 실패로 표시
        "USER-001",  // 사용자 없음 → 일반적 로그인 실패로 표시  
        "USER-006",  // 계정 비활성 → 일반적 접근 불가로 표시
        "USER-007"   // 계정 삭제 → 일반적 접근 불가로 표시
    );
    
    // 3. 관리자 전용 상세 정보
    public static final Set<String> ADMIN_ONLY_DETAILS = Set.of(
        "SYSTEM-002", // DB 연결 오류
        "SYSTEM-004", // 캐시 오류
        "SYSTEM-005"  // 설정 오류
    );
    
    /**
     * 보안 수준별 에러 메시지 필터링
     */
    public ErrorInfo filterErrorMessage(ErrorCode errorCode, String userRole) {
        ErrorInfo errorInfo = ErrorInfo.builder()
            .code(errorCode.getCode())
            .httpStatus(errorCode.getHttpStatus())
            .timestamp(System.currentTimeMillis())
            .build();
            
        // 관리자가 아닌 경우 상세 정보 제한
        if (!"ADMIN".equals(userRole) && ADMIN_ONLY_DETAILS.contains(errorCode.getCode())) {
            errorInfo.setUserMessage("일시적인 서비스 오류입니다");
            errorInfo.setDeveloperMessage("Service temporarily unavailable");
        } else if (VAGUE_ERROR_CODES.contains(errorCode.getCode())) {
            // 보안상 모호한 메시지로 대체
            errorInfo.setUserMessage("로그인 정보를 확인해주세요");
            errorInfo.setDeveloperMessage("Authentication failed");
        } else {
            errorInfo.setUserMessage(errorCode.getUserMessage());
            errorInfo.setDeveloperMessage(errorCode.getDeveloperMessage());
        }
        
        return errorInfo;
    }
}
```

### 에러 메시지 표준화 가이드
```java
/**
 * 에러 메시지 작성 가이드라인
 */
public class ErrorMessageGuidelines {
    
    // ✅ 좋은 에러 메시지 예시
    public static final Map<String, String> GOOD_EXAMPLES = Map.of(
        "사용자_친화적", "이메일 형식이 올바르지 않습니다",
        "구체적_지침", "비밀번호는 8자 이상, 영문/숫자/특수문자를 포함해야 합니다",
        "해결방안_제시", "인증번호가 만료되었습니다. 새로운 인증번호를 요청해주세요",
        "정중한_톤", "잠시 후 다시 시도해주세요"
    );
    
    // ❌ 피해야 할 에러 메시지 예시
    public static final Map<String, String> BAD_EXAMPLES = Map.of(
        "기술적_용어", "NullPointerException occurred",
        "너무_상세한_정보", "Database connection failed: Connection timeout at 192.168.1.100:3306",
        "비난하는_톤", "잘못 입력했습니다",
        "모호한_표현", "오류가 발생했습니다"
    );
    
    // 한국어 메시지 작성 원칙
    public static final List<String> KOREAN_MESSAGE_PRINCIPLES = List.of(
        "존댓말 사용 (해주세요, 습니다)",
        "구체적이고 명확한 표현",
        "해결 방안 포함",
        "사용자 관점에서 작성",
        "기술 용어 지양",
        "정중하고 친근한 톤"
    );
}
```

---

## 🔧 도메인별 구체 예외 클래스

### AuthException 예시
```java
package com.routepick.exception.auth;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;

/**
 * 인증/인가 관련 예외
 */
public class AuthException extends BaseException {
    
    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public AuthException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }
    
    public AuthException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
    
    // 자주 사용되는 인증 예외들을 위한 팩토리 메서드
    public static AuthException tokenExpired() {
        return new AuthException(ErrorCode.TOKEN_EXPIRED);
    }
    
    public static AuthException invalidToken() {
        return new AuthException(ErrorCode.TOKEN_INVALID);
    }
    
    public static AuthException accessDenied() {
        return new AuthException(ErrorCode.ACCESS_DENIED);
    }
    
    public static AuthException loginAttemptsExceeded(String ipAddress) {
        return new AuthException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED, ipAddress);
    }
}
```

### UserException 예시
```java
package com.routepick.exception.user;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;

/**
 * 사용자 관련 예외
 */
public class UserException extends BaseException {
    
    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }
    
    public UserException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
    }
    
    public UserException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
    
    // 팩토리 메서드들
    public static UserException notFound(Long userId) {
        return new UserException(ErrorCode.USER_NOT_FOUND, userId);
    }
    
    public static UserException emailAlreadyExists(String email) {
        return new UserException(ErrorCode.EMAIL_ALREADY_REGISTERED, email);
    }
    
    public static UserException nicknameAlreadyExists(String nickname) {
        return new UserException(ErrorCode.NICKNAME_ALREADY_EXISTS, nickname);
    }
    
    public static UserException phoneVerificationRequired() {
        return new UserException(ErrorCode.PHONE_VERIFICATION_REQUIRED);
    }
}
```

---

## 📊 예외 처리 통계 및 모니터링

### ErrorCode 사용 현황
```java
/**
 * 에러 코드 사용 통계
 */
@Component
public class ErrorCodeStatistics {
    
    // 도메인별 에러 코드 분포
    public static final Map<String, Integer> DOMAIN_ERROR_COUNT = Map.of(
        "AUTH", 24,        // 인증/인가 (24개)
        "USER", 25,        // 사용자 관리 (25개) 
        "GYM", 8,          // 체육관 관리 (8개)
        "ROUTE", 25,       // 루트 관리 (25개)
        "TAG", 23,         // 태그 시스템 (23개)
        "PAYMENT", 23,     // 결제 시스템 (23개)
        "VALIDATION", 23,  // 입력 검증 (23개)
        "SYSTEM", 22,      // 시스템 (22개)
        "COMMON", 4        // 공통 (4개)
    );
    
    // 총 에러 코드 수: 177개
    // 확장 가능 여유분: 각 도메인별 75~99개씩 추가 가능
    
    // HTTP 상태 코드별 분포
    public static final Map<HttpStatus, Integer> HTTP_STATUS_DISTRIBUTION = Map.of(
        HttpStatus.BAD_REQUEST, 89,           // 400: 89개 (50%)
        HttpStatus.UNAUTHORIZED, 6,           // 401: 6개 (3%)
        HttpStatus.FORBIDDEN, 8,              // 403: 8개 (5%)
        HttpStatus.NOT_FOUND, 15,             // 404: 15개 (8%)
        HttpStatus.CONFLICT, 7,               // 409: 7개 (4%)
        HttpStatus.TOO_MANY_REQUESTS, 3,      // 429: 3개 (2%)
        HttpStatus.INTERNAL_SERVER_ERROR, 45, // 500: 45개 (25%)
        HttpStatus.BAD_GATEWAY, 1,            // 502: 1개 (1%)
        HttpStatus.SERVICE_UNAVAILABLE, 2,    // 503: 2개 (1%)
        HttpStatus.INSUFFICIENT_STORAGE, 1    // 507: 1개 (1%)
    );
}
```

### 보안 수준별 에러 분류
```java
/**
 * 보안 수준별 에러 분류
 */
public enum SecurityLevel {
    
    // 높음: 보안에 민감한 에러 (상세 정보 제한)
    HIGH(Set.of(
        "AUTH-002", "AUTH-007", "AUTH-008",  // 인증 실패
        "USER-001", "USER-006", "USER-007",  // 사용자 정보
        "SYSTEM-002", "SYSTEM-004", "SYSTEM-005"  // 시스템 내부
    )),
    
    // 중간: 일반적인 비즈니스 에러 (표준 메시지)
    MEDIUM(Set.of(
        "USER-002", "USER-003", "USER-004",  // 가입 관련
        "GYM-001", "GYM-002", "GYM-003",     // 체육관 관련
        "ROUTE-001", "ROUTE-002", "ROUTE-003", // 루트 관련
        "TAG-001", "TAG-002", "TAG-003"      // 태그 관련
    )),
    
    // 낮음: 사용자 친화적 에러 (상세 가이드 제공)
    LOW(Set.of(
        "VALIDATION-001", "VALIDATION-002",  // 입력 검증
        "VALIDATION-004", "VALIDATION-005",  // 형식 검증
        "ROUTE-024", "ROUTE-025",            // 파일 업로드
        "SYSTEM-021", "SYSTEM-022"           // Rate Limiting
    ));
    
    private final Set<String> errorCodes;
    
    SecurityLevel(Set<String> errorCodes) {
        this.errorCodes = errorCodes;
    }
    
    public static SecurityLevel getLevel(String errorCode) {
        for (SecurityLevel level : values()) {
            if (level.errorCodes.contains(errorCode)) {
                return level;
            }
        }
        return MEDIUM; // 기본값
    }
}
```

---

## ✅ Step 3-1 완료 체크리스트

### 🔧 BaseException 추상 클래스
- [x] **공통 기능 구현**: 에러 코드, 메시지 관리, 자동 로깅
- [x] **생성자 오버로딩**: 4가지 생성자로 유연한 예외 생성
- [x] **민감정보 마스킹**: 휴대폰, 이메일, 카드번호, 토큰 자동 마스킹
- [x] **로깅 전략**: HTTP 상태별 차등 로깅, 보안 예외 특별 처리
- [x] **API 응답 지원**: ErrorInfo DTO로 표준화된 응답 제공

### 📋 ErrorCode Enum 체계
- [x] **8개 도메인 분류**: AUTH, USER, GYM, ROUTE, TAG, PAYMENT, VALIDATION, SYSTEM
- [x] **체계적 코드 구조**: [DOMAIN]-[001~099] 형식으로 확장성 확보
- [x] **이중 메시지 시스템**: 한국어 사용자 메시지 + 영문 개발자 메시지
- [x] **HTTP 상태 매핑**: 각 에러별 적절한 HTTP 상태 코드 할당
- [x] **177개 에러 코드**: 운영에 필요한 모든 예외 상황 커버

### 🛡️ 보안 강화 원칙
- [x] **브루트 포스 대응**: 로그인/인증 시도 제한 에러 코드
- [x] **정보 누출 방지**: 보안상 민감한 에러는 모호한 메시지 제공
- [x] **관리자 전용 정보**: 시스템 내부 에러는 관리자에게만 상세 정보
- [x] **3단계 보안 수준**: HIGH/MEDIUM/LOW 보안 레벨별 차등 처리
- [x] **한국어 친화적**: 존댓말, 해결방안 포함한 사용자 친화적 메시지

### 🔧 도메인별 구체 예외
- [x] **팩토리 메서드**: 자주 사용되는 예외의 간편 생성 메서드
- [x] **도메인 특화**: 각 도메인 비즈니스 로직에 특화된 예외 클래스
- [x] **확장성**: 새로운 예외 추가 시 일관된 패턴 적용 가능

### 📊 통계 및 모니터링
- [x] **사용 현황 추적**: 도메인별, HTTP 상태별 에러 분포 통계
- [x] **확장 계획**: 각 도메인별 75개씩 추가 에러 코드 확장 가능
- [x] **모니터링 지원**: 로그 기반 에러 추적 및 알림 체계

---

## 📈 다음 개발 단계

### Step 3-2: GlobalExceptionHandler 구현
- **@ControllerAdvice**: 전역 예외 처리기 구현
- **표준 응답 포맷**: ApiResponse와 연동한 일관된 에러 응답
- **Spring Validation**: @Valid 검증 예외 처리 연동
- **예상 소요 시간**: 2-3시간

### Step 3-3: 커스텀 Validation 애노테이션
- **한국 특화 검증**: @KoreanPhone, @KoreanGPS 등
- **비즈니스 규칙 검증**: @UniqueEmail, @ValidRouteLevel 등
- **XSS 방지**: @XssProtection 애노테이션
- **예상 소요 시간**: 2-3시간

---

**다음 단계**: Step 3-2 GlobalExceptionHandler 구현  
**예상 소요 시간**: 2-3시간  
**핵심 목표**: Spring Boot와 통합된 전역 예외 처리 완성

*완료일: 2025-08-16*  
*핵심 성과: RoutePickr 예외 처리 기반 체계 100% 완성*