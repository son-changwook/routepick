# Step 3-2a: 인증 및 사용자 예외 클래스

> AuthException, UserException 도메인별 예외 클래스 구현  
> 생성일: 2025-08-20  
> 분할: step3-2_domain_exceptions.md → 인증/사용자 도메인 추출  
> 기반 분석: step3-1_exception_base.md

---

## 🎯 인증 및 사용자 예외 클래스 개요

### 구현 원칙
- **BaseException 상속**: 공통 기능 활용 (로깅, 마스킹, 추적)
- **도메인 특화**: 각 도메인 비즈니스 로직에 특화된 생성자 및 메서드
- **팩토리 메서드**: 자주 사용되는 예외의 간편 생성
- **컨텍스트 정보**: 도메인별 추가 정보 포함
- **보안 강화**: 민감정보 보호 및 적절한 로깅 레벨

### 2개 도메인 예외 클래스
```
AuthException        # 인증/인가 (JWT, 소셜 로그인, 권한)
UserException        # 사용자 관리 (가입, 프로필, 본인인증)
```

---

## 🔐 AuthException (인증/인가 관련)

### 클래스 구조
```java
package com.routepick.exception.auth;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

/**
 * 인증/인가 관련 예외 클래스
 * 
 * 주요 기능:
 * - JWT 토큰 관련 예외 처리
 * - 소셜 로그인 4개 제공자 예외 처리
 * - 권한 기반 접근 제어 예외
 * - 브루트 포스 공격 대응
 * - 보안 강화 로깅
 */
@Getter
public class AuthException extends BaseException {
    
    private final String requestIp;        // 요청 IP (보안 추적용)
    private final String userAgent;       // User Agent (보안 추적용)
    private final String requestPath;     // 요청 경로 (권한 검증용)
    private final Long attemptUserId;     // 시도한 사용자 ID (있는 경우)
    
    // 기본 생성자
    public AuthException(ErrorCode errorCode) {
        super(errorCode);
        this.requestIp = null;
        this.userAgent = null;
        this.requestPath = null;
        this.attemptUserId = null;
    }
    
    // 보안 컨텍스트 포함 생성자
    public AuthException(ErrorCode errorCode, String requestIp, String userAgent, String requestPath) {
        super(errorCode);
        this.requestIp = requestIp;
        this.userAgent = userAgent;
        this.requestPath = requestPath;
        this.attemptUserId = null;
    }
    
    // 사용자 ID 포함 생성자
    public AuthException(ErrorCode errorCode, Long attemptUserId, String requestIp) {
        super(errorCode);
        this.requestIp = requestIp;
        this.userAgent = null;
        this.requestPath = null;
        this.attemptUserId = attemptUserId;
    }
    
    // 파라미터화된 메시지 생성자
    public AuthException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
        this.requestIp = null;
        this.userAgent = null;
        this.requestPath = null;
        this.attemptUserId = null;
    }
    
    // 원인 예외 포함 생성자
    public AuthException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
        this.requestIp = null;
        this.userAgent = null;
        this.requestPath = null;
        this.attemptUserId = null;
    }
    
    // ========== 팩토리 메서드 (자주 사용되는 예외) ==========
    
    /**
     * JWT 토큰 만료 예외
     */
    public static AuthException tokenExpired() {
        return new AuthException(ErrorCode.TOKEN_EXPIRED);
    }
    
    /**
     * 유효하지 않은 JWT 토큰 예외
     */
    public static AuthException invalidToken() {
        return new AuthException(ErrorCode.TOKEN_INVALID);
    }
    
    /**
     * 토큰 누락 예외
     */
    public static AuthException tokenMissing() {
        return new AuthException(ErrorCode.TOKEN_MISSING);
    }
    
    /**
     * 로그인 시도 횟수 초과 (브루트 포스 대응)
     */
    public static AuthException loginAttemptsExceeded(String requestIp) {
        return new AuthException(ErrorCode.LOGIN_ATTEMPTS_EXCEEDED, requestIp, null, null);
    }
    
    /**
     * 계정 잠금 예외
     */
    public static AuthException accountLocked(Long userId, String reason) {
        return new AuthException(ErrorCode.ACCOUNT_LOCKED, userId, null);
    }
    
    /**
     * 접근 권한 거부 예외
     */
    public static AuthException accessDenied(String requestPath, Long userId) {
        AuthException exception = new AuthException(ErrorCode.ACCESS_DENIED);
        exception.requestPath = requestPath;
        exception.attemptUserId = userId;
        return exception;
    }
    
    /**
     * 관리자 권한 필요 예외
     */
    public static AuthException adminAccessRequired(Long userId, String requestPath) {
        return new AuthException(ErrorCode.ADMIN_ACCESS_REQUIRED, userId, null);
    }
    
    /**
     * 체육관 관리자 권한 필요 예외
     */
    public static AuthException gymAdminAccessRequired(Long userId, Long gymId) {
        return new AuthException(ErrorCode.GYM_ADMIN_ACCESS_REQUIRED, userId, gymId);
    }
    
    // ========== 소셜 로그인 관련 팩토리 메서드 ==========
    
    /**
     * 소셜 로그인 실패 (4개 제공자: GOOGLE, KAKAO, NAVER, FACEBOOK)
     */
    public static AuthException socialLoginFailed(String provider) {
        return new AuthException(ErrorCode.SOCIAL_LOGIN_FAILED, provider);
    }
    
    /**
     * 지원하지 않는 소셜 제공자
     */
    public static AuthException socialProviderNotSupported(String provider) {
        return new AuthException(ErrorCode.SOCIAL_PROVIDER_NOT_SUPPORTED, provider);
    }
    
    /**
     * 소셜 토큰 유효하지 않음
     */
    public static AuthException socialTokenInvalid(String provider) {
        return new AuthException(ErrorCode.SOCIAL_TOKEN_INVALID, provider);
    }
    
    /**
     * 소셜 계정 이미 연결됨
     */
    public static AuthException socialAccountAlreadyLinked(String provider, String socialId) {
        return new AuthException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED, provider, socialId);
    }
    
    // ========== 이메일/비밀번호 관련 팩토리 메서드 ==========
    
    /**
     * 유효하지 않은 이메일 형식
     */
    public static AuthException invalidEmail(String email) {
        return new AuthException(ErrorCode.INVALID_EMAIL, email);
    }
    
    /**
     * 잘못된 비밀번호
     */
    public static AuthException invalidPassword() {
        // 보안상 세부 정보 포함하지 않음
        return new AuthException(ErrorCode.INVALID_PASSWORD);
    }
    
    /**
     * 보안 컨텍스트 정보 포함한 예외 생성
     */
    public AuthException withSecurityContext(String requestIp, String userAgent, String requestPath) {
        AuthException newException = new AuthException(this.getErrorCode());
        newException.requestIp = requestIp;
        newException.userAgent = userAgent;
        newException.requestPath = requestPath;
        return newException;
    }
}
```

---

## 👤 UserException (사용자 관련)

### 클래스 구조
```java
package com.routepick.exception.user;

import com.routepick.common.ErrorCode;
import com.routepick.exception.BaseException;
import lombok.Getter;

/**
 * 사용자 관련 예외 클래스
 * 
 * 주요 기능:
 * - 사용자 가입/조회/관리 예외
 * - 한국 특화 검증 (휴대폰 번호, 한글 닉네임)
 * - 본인인증 프로세스 예외
 * - 프로필 관리 예외
 * - 사용자 상태 관리 예외
 */
@Getter
public class UserException extends BaseException {
    
    private final Long userId;           // 관련 사용자 ID
    private final String email;         // 관련 이메일 (중복 검사 등)
    private final String nickname;      // 관련 닉네임 (중복 검사 등)
    private final String phoneNumber;   // 관련 휴대폰 번호 (본인인증 등)
    
    // 기본 생성자
    public UserException(ErrorCode errorCode) {
        super(errorCode);
        this.userId = null;
        this.email = null;
        this.nickname = null;
        this.phoneNumber = null;
    }
    
    // 사용자 ID 포함 생성자
    public UserException(ErrorCode errorCode, Long userId) {
        super(errorCode);
        this.userId = userId;
        this.email = null;
        this.nickname = null;
        this.phoneNumber = null;
    }
    
    // 파라미터화된 메시지 생성자
    public UserException(ErrorCode errorCode, Object... messageArgs) {
        super(errorCode, messageArgs);
        this.userId = null;
        this.email = null;
        this.nickname = null;
        this.phoneNumber = null;
    }
    
    // 원인 예외 포함 생성자
    public UserException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
        this.userId = null;
        this.email = null;
        this.nickname = null;
        this.phoneNumber = null;
    }
    
    // 상세 정보 포함 생성자
    private UserException(ErrorCode errorCode, Long userId, String email, String nickname, String phoneNumber) {
        super(errorCode);
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.phoneNumber = phoneNumber;
    }
    
    // ========== 팩토리 메서드 (사용자 조회/관리) ==========
    
    /**
     * 사용자를 찾을 수 없음
     */
    public static UserException notFound(Long userId) {
        return new UserException(ErrorCode.USER_NOT_FOUND, userId, null, null, null);
    }
    
    /**
     * 이메일로 사용자를 찾을 수 없음
     */
    public static UserException notFoundByEmail(String email) {
        return new UserException(ErrorCode.USER_NOT_FOUND, null, email, null, null);
    }
    
    /**
     * 이미 존재하는 사용자
     */
    public static UserException alreadyExists(String email) {
        return new UserException(ErrorCode.USER_ALREADY_EXISTS, null, email, null, null);
    }
    
    /**
     * 이메일 중복
     */
    public static UserException emailAlreadyRegistered(String email) {
        return new UserException(ErrorCode.EMAIL_ALREADY_REGISTERED, null, email, null, null);
    }
    
    /**
     * 닉네임 중복
     */
    public static UserException nicknameAlreadyExists(String nickname) {
        return new UserException(ErrorCode.NICKNAME_ALREADY_EXISTS, null, null, nickname, null);
    }
    
    /**
     * 사용자 프로필을 찾을 수 없음
     */
    public static UserException profileNotFound(Long userId) {
        return new UserException(ErrorCode.USER_PROFILE_NOT_FOUND, userId, null, null, null);
    }
    
    /**
     * 비활성화된 계정
     */
    public static UserException inactive(Long userId) {
        return new UserException(ErrorCode.USER_INACTIVE, userId, null, null, null);
    }
    
    /**
     * 삭제된 계정
     */
    public static UserException deleted(Long userId) {
        return new UserException(ErrorCode.USER_DELETED, userId, null, null, null);
    }
    
    // ========== 본인인증 관련 팩토리 메서드 ==========
    
    /**
     * 휴대폰 인증 필요
     */
    public static UserException phoneVerificationRequired(Long userId) {
        return new UserException(ErrorCode.PHONE_VERIFICATION_REQUIRED, userId, null, null, null);
    }
    
    /**
     * 휴대폰 인증 실패
     */
    public static UserException phoneVerificationFailed(String phoneNumber) {
        return new UserException(ErrorCode.PHONE_VERIFICATION_FAILED, null, null, null, phoneNumber);
    }
    
    /**
     * 인증번호 오류
     */
    public static UserException verificationCodeInvalid(String phoneNumber) {
        return new UserException(ErrorCode.VERIFICATION_CODE_INVALID, null, null, null, phoneNumber);
    }
    
    /**
     * 인증번호 만료
     */
    public static UserException verificationCodeExpired(String phoneNumber) {
        return new UserException(ErrorCode.VERIFICATION_CODE_EXPIRED, null, null, null, phoneNumber);
    }
    
    /**
     * 잘못된 휴대폰 번호 형식 (한국 특화)
     */
    public static UserException invalidPhoneNumber(String phoneNumber) {
        return new UserException(ErrorCode.PHONE_NUMBER_INVALID, null, null, null, phoneNumber);
    }
    
    // ========== 한국 특화 검증 메서드 ==========
    
    /**
     * 한국 휴대폰 번호 형식 검증
     */
    public static boolean isValidKoreanPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return false;
        
        // 한국 휴대폰 번호 패턴: 010-XXXX-XXXX, 011-XXX-XXXX 등
        return phoneNumber.matches("^01[0-9]-\\d{3,4}-\\d{4}$");
    }
    
    /**
     * 한글 닉네임 검증 (2-10자, 한글/영문/숫자)
     */
    public static boolean isValidKoreanNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) return false;
        
        // 한글, 영문, 숫자 조합 2-10자
        return nickname.matches("^[가-힣a-zA-Z0-9]{2,10}$");
    }
    
    /**
     * 유효한 사용자 상태 검증
     */
    public static void validateUserStatus(String userStatus) {
        if (userStatus == null) {
            throw new UserException(ErrorCode.REQUIRED_FIELD_MISSING, "userStatus");
        }
        
        if (!"ACTIVE".equals(userStatus) && !"INACTIVE".equals(userStatus) && !"DELETED".equals(userStatus)) {
            throw new UserException(ErrorCode.INVALID_INPUT_FORMAT, "userStatus: " + userStatus);
        }
    }
}
```

---

## ✅ 인증/사용자 예외 완료 체크리스트

### 🔐 AuthException 구현
- [x] JWT 토큰 관련 예외 (만료, 무효, 누락)
- [x] 소셜 로그인 4개 제공자 예외 처리
- [x] 권한 기반 접근 제어 예외
- [x] 브루트 포스 공격 대응
- [x] 보안 컨텍스트 정보 포함
- [x] 관리자/체육관 관리자 권한 예외

### 👤 UserException 구현  
- [x] 사용자 CRUD 관련 예외
- [x] 이메일/닉네임 중복 검사 예외
- [x] 한국 특화 휴대폰 번호 검증
- [x] 한글 닉네임 검증 (2-10자)
- [x] 본인인증 프로세스 예외
- [x] 사용자 상태 관리 예외

### 보안 강화 사항
- [x] 민감정보 마스킹 (이메일, 휴대폰)
- [x] 보안 추적용 IP/UserAgent 저장
- [x] 브루트 포스 공격 대응
- [x] 적절한 로깅 레벨 설정

---

*분할 작업 1/4 완료: AuthException + UserException*  
*다음 파일: step3-2b_gym_route_exceptions.md*