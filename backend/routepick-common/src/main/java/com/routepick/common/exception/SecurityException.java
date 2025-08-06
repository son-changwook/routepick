package com.routepick.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 보안 관련 예외
 * 주로 403 Forbidden 또는 400 Bad Request 상황에서 사용
 */
public class SecurityException extends BaseException {
    
    public SecurityException(String errorCode, String message) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }
    
    public SecurityException(String errorCode, String userMessage, String developerMessage) {
        super(HttpStatus.FORBIDDEN, errorCode, userMessage, developerMessage);
    }
    
    public SecurityException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.FORBIDDEN, errorCode, message, cause);
    }
    
    // 자주 사용되는 보안 예외들을 static 메서드로 제공
    public static SecurityException fileUploadBlocked(String reason) {
        return new SecurityException("FILE_UPLOAD_BLOCKED", 
            "보안상 업로드가 차단되었습니다.", 
            "파일 업로드 차단: " + reason);
    }
    
    public static SecurityException xssDetected() {
        return new SecurityException("XSS_DETECTED", "허용되지 않는 스크립트가 감지되었습니다.");
    }
    
    public static SecurityException sqlInjectionDetected() {
        return new SecurityException("SQL_INJECTION_DETECTED", "허용되지 않는 쿼리 패턴이 감지되었습니다.");
    }
    
    public static SecurityException pathTraversalDetected() {
        return new SecurityException("PATH_TRAVERSAL_DETECTED", "경로 순회 공격이 감지되었습니다.");
    }
    
    public static SecurityException dangerousFileType(String fileType) {
        return new SecurityException("DANGEROUS_FILE_TYPE", 
            "보안상 업로드할 수 없는 파일 형식입니다.",
            "위험한 파일 타입: " + fileType);
    }
    
    public static SecurityException invalidFilename(String reason) {
        return new SecurityException("INVALID_FILENAME", 
            "파일명이 보안 정책에 위배됩니다.",
            "파일명 검증 실패: " + reason);
    }
    
    public static SecurityException fileTooBig(long actualSize, long maxSize) {
        return new SecurityException("FILE_TOO_BIG", 
            String.format("파일 크기가 제한을 초과했습니다. (최대: %dMB)", maxSize / (1024 * 1024)),
            String.format("파일 크기 초과: %d bytes > %d bytes", actualSize, maxSize));
    }
    
    public static SecurityException fileTooSmall(long actualSize, long minSize) {
        return new SecurityException("FILE_TOO_SMALL", 
            "파일이 너무 작습니다.",
            String.format("파일 크기 부족: %d bytes < %d bytes", actualSize, minSize));
    }
    
    public static SecurityException configurationError(String configName) {
        return new SecurityException("CONFIGURATION_ERROR", 
            "시스템 설정 오류가 발생했습니다.",
            "설정 오류: " + configName);
    }
    
    public static SecurityException invalidEmailFormat() {
        return new SecurityException("INVALID_EMAIL_FORMAT", 
            "올바른 이메일 형식이 아닙니다.");
    }
    
    public static SecurityException invalidInputFormat(String fieldName) {
        return new SecurityException("INVALID_INPUT_FORMAT", 
            String.format("%s 형식이 올바르지 않습니다.", fieldName));
    }
}