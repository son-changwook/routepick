package com.routepick.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 비즈니스 로직 관련 예외
 * 주로 400 Bad Request 상황에서 사용
 */
public class BusinessException extends BaseException {
    
    public BusinessException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
    
    public BusinessException(String errorCode, String userMessage, String developerMessage) {
        super(HttpStatus.BAD_REQUEST, errorCode, userMessage, developerMessage);
    }
    
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, errorCode, message, cause);
    }
    
    // 자주 사용되는 비즈니스 예외들을 static 메서드로 제공
    public static BusinessException invalidInput(String message) {
        return new BusinessException("INVALID_INPUT", message);
    }
    
    public static BusinessException rateLimitExceeded() {
        return new BusinessException("RATE_LIMIT_EXCEEDED", "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
    }
    
    public static BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
    }
    
    public static BusinessException expiredToken() {
        return new BusinessException("EXPIRED_TOKEN", "토큰이 만료되었습니다. 다시 로그인해주세요.");
    }
    
    public static BusinessException invalidToken() {
        return new BusinessException("INVALID_TOKEN", "유효하지 않은 토큰입니다.");
    }
}