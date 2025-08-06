package com.routepick.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 인증 관련 예외
 * 주로 401 Unauthorized 상황에서 사용
 */
public class AuthenticationException extends BaseException {
    
    public AuthenticationException(String errorCode, String message) {
        super(HttpStatus.UNAUTHORIZED, errorCode, message);
    }
    
    public AuthenticationException(String errorCode, String userMessage, String developerMessage) {
        super(HttpStatus.UNAUTHORIZED, errorCode, userMessage, developerMessage);
    }
    
    public AuthenticationException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.UNAUTHORIZED, errorCode, message, cause);
    }
    
    // 자주 사용되는 인증 예외들을 static 메서드로 제공
    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
    }
    
    public static AuthenticationException accountDisabled() {
        return new AuthenticationException("ACCOUNT_DISABLED", "비활성화된 계정입니다.");
    }
    
    public static AuthenticationException accountSuspended() {
        return new AuthenticationException("ACCOUNT_SUSPENDED", "정지된 계정입니다.");
    }
    
    public static AuthenticationException accountDeleted() {
        return new AuthenticationException("ACCOUNT_DELETED", "삭제된 계정입니다.");
    }
    
    public static AuthenticationException invalidToken() {
        return new AuthenticationException("INVALID_TOKEN", "유효하지 않은 토큰입니다.");
    }
    
    public static AuthenticationException expiredToken() {
        return new AuthenticationException("EXPIRED_TOKEN", "만료된 토큰입니다.");
    }
    
    public static AuthenticationException invalidRefreshToken() {
        return new AuthenticationException("INVALID_REFRESH_TOKEN", "유효하지 않은 리프레시 토큰입니다.");
    }
    
    public static AuthenticationException tokenTypeMismatch() {
        return new AuthenticationException("TOKEN_TYPE_MISMATCH", "토큰 타입이 일치하지 않습니다.");
    }
    
    public static AuthenticationException sessionExpired() {
        return new AuthenticationException("SESSION_EXPIRED", "세션이 만료되었습니다. 다시 로그인해주세요.");
    }
}