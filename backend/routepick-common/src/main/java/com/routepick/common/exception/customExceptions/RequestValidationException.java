package com.routepick.common.exception.customExceptions;

/**
 * 요청 검증 실패 예외
 */
public class RequestValidationException extends RuntimeException {
    
    public RequestValidationException(String message) {
        super(message);
    }
    
    public RequestValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
