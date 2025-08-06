package com.routepick.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 모든 커스텀 예외의 기본 클래스
 * HTTP 상태코드와 에러 코드를 명시적으로 관리합니다.
 */
@Getter
public abstract class BaseException extends RuntimeException {
    
    /**
     * HTTP 상태코드
     */
    private final HttpStatus httpStatus;
    
    /**
     * 비즈니스 에러 코드
     */
    private final String errorCode;
    
    /**
     * 사용자에게 표시할 메시지
     */
    private final String userMessage;
    
    /**
     * 개발자용 상세 메시지
     */
    private final String developerMessage;
    
    protected BaseException(HttpStatus httpStatus, String errorCode, String userMessage) {
        super(userMessage);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.developerMessage = userMessage;
    }
    
    protected BaseException(HttpStatus httpStatus, String errorCode, String userMessage, String developerMessage) {
        super(developerMessage);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.developerMessage = developerMessage;
    }
    
    protected BaseException(HttpStatus httpStatus, String errorCode, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.developerMessage = userMessage;
    }
    
    protected BaseException(HttpStatus httpStatus, String errorCode, String userMessage, String developerMessage, Throwable cause) {
        super(developerMessage, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.developerMessage = developerMessage;
    }
}