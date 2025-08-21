# Step 3-1a: BaseException 추상 클래스 설계

> RoutePickr 커스텀 예외의 기반 구조 및 BaseException 구현  
> 생성일: 2025-08-21  
> 기반 분석: step1-3_spring_boot_guide.md, step2-1_backend_structure.md  
> 세분화: step3-1_exception_base.md에서 분리

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

### ValidationException 예시
```java
package com.routepick.exception.validation;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

/**
 * 입력 검증 관련 예외
 */
@Getter
public class ValidationException extends BaseException {
    
    private final String fieldName;
    private final String inputValue;
    private final String violationType;  // XSS, SQL_INJECTION 등
    
    public ValidationException(ErrorCode errorCode, String fieldName, String inputValue) {
        super(errorCode);
        this.fieldName = fieldName;
        this.inputValue = inputValue;
        this.violationType = null;
    }
    
    public ValidationException(ErrorCode errorCode, String fieldName, String inputValue, String violationType) {
        super(errorCode);
        this.fieldName = fieldName;
        this.inputValue = inputValue;
        this.violationType = violationType;
    }
    
    // 팩토리 메서드들
    public static ValidationException xssDetected(String fieldName, String inputValue) {
        return new ValidationException(ErrorCode.POTENTIAL_XSS_DETECTED, fieldName, inputValue, "XSS");
    }
    
    public static ValidationException sqlInjectionAttempt(String fieldName, String inputValue) {
        return new ValidationException(ErrorCode.SQL_INJECTION_ATTEMPT, fieldName, inputValue, "SQL_INJECTION");
    }
    
    public static ValidationException invalidFormat(String fieldName, String inputValue) {
        return new ValidationException(ErrorCode.INVALID_INPUT_FORMAT, fieldName, inputValue);
    }
    
    public static ValidationException requiredFieldMissing(String fieldName) {
        return new ValidationException(ErrorCode.REQUIRED_FIELD_MISSING, fieldName, null);
    }
}
```

---

## 📊 보안 수준별 에러 분류

### 보안 수준 정의
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

## ✅ Step 3-1a 완료 체크리스트

### 🔧 BaseException 추상 클래스
- [x] **공통 기능 구현**: 에러 코드, 메시지 관리, 자동 로깅
- [x] **생성자 오버로딩**: 4가지 생성자로 유연한 예외 생성
- [x] **민감정보 마스킹**: 휴대폰, 이메일, 카드번호, 토큰 자동 마스킹
- [x] **로깅 전략**: HTTP 상태별 차등 로깅, 보안 예외 특별 처리
- [x] **API 응답 지원**: ErrorInfo DTO로 표준화된 응답 제공

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
- [x] **ValidationException**: 보안 위협 탐지 기능 포함

### 📊 보안 레벨 관리
- [x] **3단계 분류**: HIGH/MEDIUM/LOW 보안 수준별 에러 분류
- [x] **동적 필터링**: 사용자 권한에 따른 에러 메시지 동적 조정
- [x] **가이드라인**: 한국어 에러 메시지 작성 원칙 정립

---

**다음 단계**: step3-1b_error_codes.md (ErrorCode Enum 체계)  
**관련 파일**: step3-1c_statistics_monitoring.md (통계 및 모니터링)

*생성일: 2025-08-21*  
*핵심 성과: RoutePickr 예외 처리 기반 구조 완성*