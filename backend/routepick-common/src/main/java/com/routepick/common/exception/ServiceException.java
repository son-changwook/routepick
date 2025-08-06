package com.routepick.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 서비스 처리 관련 예외
 * 주로 500 Internal Server Error 또는 503 Service Unavailable 상황에서 사용
 */
public class ServiceException extends BaseException {
    
    public ServiceException(String errorCode, String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, message);
    }
    
    public ServiceException(String errorCode, String userMessage, String developerMessage) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, userMessage, developerMessage);
    }
    
    public ServiceException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, message, cause);
    }
    
    public ServiceException(HttpStatus httpStatus, String errorCode, String message) {
        super(httpStatus, errorCode, message);
    }
    
    public ServiceException(HttpStatus httpStatus, String errorCode, String userMessage, String developerMessage) {
        super(httpStatus, errorCode, userMessage, developerMessage);
    }
    
    public ServiceException(HttpStatus httpStatus, String errorCode, String message, Throwable cause) {
        super(httpStatus, errorCode, message, cause);
    }
    
    // 자주 사용되는 서비스 예외들을 static 메서드로 제공
    public static ServiceException emailSendFailed(String reason) {
        return new ServiceException("EMAIL_SEND_FAILED", 
            "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.",
            "이메일 발송 실패: " + reason);
    }
    
    public static ServiceException emailSendFailed(Throwable cause) {
        return new ServiceException("EMAIL_SEND_FAILED", 
            "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요.",
            cause);
    }
    
    public static ServiceException sessionCreationFailed(Throwable cause) {
        return new ServiceException("SESSION_CREATION_FAILED", 
            "세션 생성에 실패했습니다. 잠시 후 다시 시도해주세요.",
            cause);
    }
    
    public static ServiceException externalServiceUnavailable(String serviceName) {
        return new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_SERVICE_UNAVAILABLE", 
            "외부 서비스와 연결할 수 없습니다. 잠시 후 다시 시도해주세요.",
            "외부 서비스 사용 불가: " + serviceName);
    }
    
    public static ServiceException databaseError(String operation, Throwable cause) {
        return new ServiceException("DATABASE_ERROR", 
            "데이터베이스 처리 중 오류가 발생했습니다.",
            cause);
    }
    
    public static ServiceException configurationError(String configName) {
        return new ServiceException("CONFIGURATION_ERROR", 
            "시스템 설정 오류가 발생했습니다.");
    }
    
    public static ServiceException unexpectedError(String operation, Throwable cause) {
        return new ServiceException("UNEXPECTED_ERROR", 
            "예상치 못한 오류가 발생했습니다. 관리자에게 문의하세요.",
            cause);
    }
}