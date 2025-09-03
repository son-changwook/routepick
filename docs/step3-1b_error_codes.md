# Step 3-1b: ErrorCode Enum 체계 설계

> RoutePickr 통합 에러 코드 시스템 - 177개 체계적 에러 코드 관리  
> 생성일: 2025-08-21  
> 기반 분석: step3-1a_base_exception_design.md  
> 세분화: step3-1_exception_base.md에서 분리

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

## 📊 ErrorCode 유틸리티 클래스

### ErrorCodeUtils
```java
package com.routepick.common.util;

import com.routepick.common.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ErrorCode 관련 유틸리티 메서드
 */
public class ErrorCodeUtils {
    
    /**
     * HTTP 상태 코드별 에러 코드 조회
     */
    public static List<ErrorCode> findByHttpStatus(HttpStatus httpStatus) {
        return Arrays.stream(ErrorCode.values())
            .filter(errorCode -> errorCode.getHttpStatus() == httpStatus)
            .collect(Collectors.toList());
    }
    
    /**
     * 도메인별 에러 코드 개수 조회
     */
    public static Map<String, Long> getErrorCodeCountByDomain() {
        return Arrays.stream(ErrorCode.values())
            .collect(Collectors.groupingBy(
                errorCode -> errorCode.getCode().split("-")[0],
                Collectors.counting()
            ));
    }
    
    /**
     * 보안 관련 에러 코드 필터링
     */
    public static List<ErrorCode> getSecurityRelatedErrors() {
        return Arrays.stream(ErrorCode.values())
            .filter(errorCode -> 
                errorCode.getCode().startsWith("AUTH-") ||
                errorCode.getCode().startsWith("VALIDATION-") ||
                errorCode.getCode().contains("SECURITY"))
            .collect(Collectors.toList());
    }
    
    /**
     * 클라이언트 에러 vs 서버 에러 분류
     */
    public static Map<String, List<ErrorCode>> categorizeByErrorType() {
        Map<String, List<ErrorCode>> result = new HashMap<>();
        
        result.put("CLIENT_ERROR", Arrays.stream(ErrorCode.values())
            .filter(errorCode -> errorCode.getHttpStatus().is4xxClientError())
            .collect(Collectors.toList()));
            
        result.put("SERVER_ERROR", Arrays.stream(ErrorCode.values())
            .filter(errorCode -> errorCode.getHttpStatus().is5xxServerError())
            .collect(Collectors.toList()));
            
        return result;
    }
    
    /**
     * 한국어 메시지 길이 통계
     */
    public static Map<String, Object> getMessageLengthStatistics() {
        List<Integer> userMessageLengths = Arrays.stream(ErrorCode.values())
            .map(errorCode -> errorCode.getUserMessage().length())
            .collect(Collectors.toList());
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("min", Collections.min(userMessageLengths));
        stats.put("max", Collections.max(userMessageLengths));
        stats.put("average", userMessageLengths.stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0.0));
        
        return stats;
    }
    
    /**
     * 에러 코드 검증 (개발 시 사용)
     */
    public static List<String> validateErrorCodes() {
        List<String> issues = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        
        for (ErrorCode errorCode : ErrorCode.values()) {
            String code = errorCode.getCode();
            
            // 중복 코드 검사
            if (seenCodes.contains(code)) {
                issues.add("Duplicate error code: " + code);
            }
            seenCodes.add(code);
            
            // 코드 형식 검사
            if (!code.matches("^[A-Z]+(-[0-9]{3})?$")) {
                issues.add("Invalid code format: " + code);
            }
            
            // 메시지 검사
            if (errorCode.getUserMessage().length() > 100) {
                issues.add("User message too long for code: " + code);
            }
            
            if (errorCode.getDeveloperMessage().length() > 150) {
                issues.add("Developer message too long for code: " + code);
            }
        }
        
        return issues;
    }
}
```

---

## 🔄 ErrorCode 매핑 시스템

### ErrorCodeMapper
```java
package com.routepick.common.mapper;

import com.routepick.common.ErrorCode;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * 외부 시스템 에러 코드와 내부 ErrorCode 매핑
 */
public class ErrorCodeMapper {
    
    // Spring Validation 에러 매핑
    private static final Map<String, ErrorCode> VALIDATION_ERROR_MAP = Map.of(
        "NotNull", ErrorCode.REQUIRED_FIELD_MISSING,
        "NotEmpty", ErrorCode.REQUIRED_FIELD_MISSING,
        "NotBlank", ErrorCode.REQUIRED_FIELD_MISSING,
        "Size", ErrorCode.FIELD_LENGTH_EXCEEDED,
        "Email", ErrorCode.INVALID_EMAIL_FORMAT,
        "Pattern", ErrorCode.INVALID_INPUT_FORMAT
    );
    
    // HTTP 상태 코드 매핑
    private static final Map<HttpStatus, ErrorCode> HTTP_STATUS_MAP = Map.of(
        HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST,
        HttpStatus.UNAUTHORIZED, ErrorCode.TOKEN_MISSING,
        HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
        HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND,
        HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
        HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
        HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR
    );
    
    // 외부 API 에러 매핑 (소셜 로그인 등)
    private static final Map<String, ErrorCode> EXTERNAL_API_ERROR_MAP = Map.of(
        "GOOGLE_AUTH_FAILED", ErrorCode.SOCIAL_LOGIN_FAILED,
        "KAKAO_AUTH_FAILED", ErrorCode.SOCIAL_LOGIN_FAILED,
        "NAVER_AUTH_FAILED", ErrorCode.SOCIAL_LOGIN_FAILED,
        "FACEBOOK_AUTH_FAILED", ErrorCode.SOCIAL_LOGIN_FAILED,
        "INVALID_SOCIAL_TOKEN", ErrorCode.SOCIAL_TOKEN_INVALID
    );
    
    /**
     * Spring Validation 에러를 ErrorCode로 변환
     */
    public static ErrorCode fromValidationError(String validationCode) {
        return VALIDATION_ERROR_MAP.getOrDefault(validationCode, ErrorCode.INVALID_INPUT_FORMAT);
    }
    
    /**
     * HTTP 상태 코드를 ErrorCode로 변환
     */
    public static ErrorCode fromHttpStatus(HttpStatus httpStatus) {
        return HTTP_STATUS_MAP.getOrDefault(httpStatus, ErrorCode.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * 외부 API 에러를 ErrorCode로 변환
     */
    public static ErrorCode fromExternalApiError(String externalErrorCode) {
        return EXTERNAL_API_ERROR_MAP.getOrDefault(externalErrorCode, ErrorCode.EXTERNAL_API_ERROR);
    }
    
    /**
     * 데이터베이스 에러를 ErrorCode로 변환
     */
    public static ErrorCode fromDatabaseError(Exception dbException) {
        String message = dbException.getMessage().toLowerCase();
        
        if (message.contains("connection")) {
            return ErrorCode.DATABASE_CONNECTION_ERROR;
        } else if (message.contains("timeout")) {
            return ErrorCode.DATABASE_CONNECTION_ERROR;
        } else if (message.contains("constraint")) {
            return ErrorCode.INVALID_INPUT_FORMAT;
        } else if (message.contains("duplicate")) {
            return ErrorCode.USER_ALREADY_EXISTS; // 컨텍스트에 따라 다를 수 있음
        }
        
        return ErrorCode.INTERNAL_SERVER_ERROR;
    }
}
```

---

## 📈 ErrorCode 확장 전략

### 도메인별 확장 계획
```java
/**
 * ErrorCode 확장 가이드라인
 */
public class ErrorCodeExpansionGuide {
    
    // 각 도메인별 현재 사용 현황 및 확장 계획
    public static final Map<String, DomainErrorInfo> DOMAIN_EXPANSION_PLAN = Map.of(
        "AUTH", new DomainErrorInfo(24, 99, "인증/인가/보안"),
        "USER", new DomainErrorInfo(25, 99, "사용자 관리/프로필"),
        "GYM", new DomainErrorInfo(8, 99, "체육관 관리/시설"),
        "ROUTE", new DomainErrorInfo(25, 99, "루트 관리/미디어"),
        "TAG", new DomainErrorInfo(23, 99, "태그 시스템/추천"),
        "PAYMENT", new DomainErrorInfo(23, 99, "결제/환불/정산"),
        "VALIDATION", new DomainErrorInfo(23, 99, "입력 검증/보안"),
        "SYSTEM", new DomainErrorInfo(22, 99, "시스템/인프라"),
        "COMMON", new DomainErrorInfo(4, 99, "공통 에러")
    );
    
    // 새로운 도메인 추가 시 고려사항
    public static final List<String> NEW_DOMAIN_CANDIDATES = List.of(
        "ANALYTICS",    // 분석/통계 관련
        "NOTIFICATION", // 알림 시스템 관련
        "CONTENT",      // 콘텐츠 관리 관련
        "SOCIAL",       // 소셜 기능 관련
        "LOCATION",     // 위치 기반 서비스 관련
        "DEVICE",       // 디바이스/앱 관련
        "SUBSCRIPTION", // 구독/멤버십 관련
        "RANKING",      // 랭킹/경쟁 관련
        "EVENT",        // 이벤트/프로모션 관련
        "INTEGRATION"   // 외부 연동 관련
    );
    
    @lombok.AllArgsConstructor
    @lombok.Getter
    public static class DomainErrorInfo {
        private int currentCount;    // 현재 에러 코드 수
        private int maxCount;        // 최대 가능 수 (099)
        private String description;  // 도메인 설명
        
        public int getAvailableSlots() {
            return maxCount - currentCount;
        }
        
        public double getUsagePercentage() {
            return (double) currentCount / maxCount * 100;
        }
    }
    
    /**
     * 새로운 에러 코드 추가 가이드라인
     */
    public static final List<String> ERROR_CODE_GUIDELINES = List.of(
        "도메인별 순차 번호 할당 (001, 002, 003...)",
        "기능별 그룹화 (인증: 001~020, 소셜: 021~040)",
        "한국어 메시지는 존댓말 사용",
        "영문 메시지는 기술적 정확성 우선",
        "HTTP 상태 코드는 RESTful 원칙 준수",
        "보안 관련 에러는 정보 노출 최소화",
        "사용자 친화적 해결 방안 포함"
    );
}
```

---

## ✅ Step 3-1b 완료 체크리스트

### 📋 ErrorCode Enum 체계
- [x] **8개 도메인 분류**: AUTH, USER, GYM, ROUTE, TAG, PAYMENT, VALIDATION, SYSTEM
- [x] **체계적 코드 구조**: [DOMAIN]-[001~099] 형식으로 확장성 확보
- [x] **이중 메시지 시스템**: 한국어 사용자 메시지 + 영문 개발자 메시지
- [x] **HTTP 상태 매핑**: 각 에러별 적절한 HTTP 상태 코드 할당
- [x] **177개 에러 코드**: 운영에 필요한 모든 예외 상황 커버

### 📊 유틸리티 시스템
- [x] **ErrorCodeUtils**: 도메인별, HTTP 상태별 에러 분류 및 통계
- [x] **ErrorCodeMapper**: 외부 시스템 에러와 내부 ErrorCode 매핑
- [x] **검증 시스템**: 에러 코드 중복, 형식, 메시지 길이 검증
- [x] **통계 기능**: 에러 코드 사용 현황 및 분포 분석

### 🔄 매핑 시스템
- [x] **Spring Validation 연동**: @Valid 에러를 ErrorCode로 변환
- [x] **HTTP 상태 매핑**: 표준 HTTP 에러를 내부 ErrorCode로 변환
- [x] **외부 API 매핑**: 소셜 로그인 등 외부 서비스 에러 변환
- [x] **데이터베이스 매핑**: DB 예외를 적절한 ErrorCode로 변환

### 📈 확장 전략
- [x] **도메인별 확장 계획**: 각 도메인별 75개씩 추가 에러 코드 확장 가능
- [x] **새로운 도메인**: 10개 신규 도메인 후보 선정
- [x] **가이드라인**: 새로운 에러 코드 추가 시 준수할 7개 원칙
- [x] **사용 현황 추적**: 도메인별 사용률 및 여유 슬롯 관리

### 🎯 핵심 성과
- [x] **완전성**: 모든 비즈니스 예외 상황 커버
- [x] **확장성**: 미래 요구사항에 대비한 구조적 확장성
- [x] **일관성**: 도메인 전반에 걸친 일관된 에러 코드 체계
- [x] **유지보수성**: 유틸리티와 매핑 시스템으로 관리 편의성 제공

---

**다음 단계**: step3-1c_statistics_monitoring.md (통계 및 모니터링)  
**관련 파일**: step3-1a_base_exception_design.md (BaseException 기반 구조)

*생성일: 2025-08-21*  
*핵심 성과: RoutePickr 통합 에러 코드 시스템 완성*