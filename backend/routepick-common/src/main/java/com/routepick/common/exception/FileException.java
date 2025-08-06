package com.routepick.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 파일 처리 관련 예외
 * 주로 400 Bad Request 상황에서 사용
 */
public class FileException extends BaseException {
    
    public FileException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
    
    public FileException(String errorCode, String userMessage, String developerMessage) {
        super(HttpStatus.BAD_REQUEST, errorCode, userMessage, developerMessage);
    }
    
    public FileException(String errorCode, String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, errorCode, message, cause);
    }
    
    // 자주 사용되는 파일 예외들을 static 메서드로 제공
    public static FileException fileNotFound(String filename) {
        return new FileException("FILE_NOT_FOUND", 
            "파일을 찾을 수 없습니다.",
            "파일 없음: " + filename);
    }
    
    public static FileException emptyFile() {
        return new FileException("EMPTY_FILE", "업로드할 파일이 비어있습니다.");
    }
    
    public static FileException noFile() {
        return new FileException("NO_FILE", "업로드할 파일이 없습니다.");
    }
    
    public static FileException unsupportedFileType(String fileType) {
        return new FileException("UNSUPPORTED_FILE_TYPE", 
            "지원하지 않는 파일 형식입니다.",
            "지원하지 않는 파일 타입: " + fileType);
    }
    
    public static FileException fileUploadFailed(String reason) {
        return new FileException("FILE_UPLOAD_FAILED", 
            "파일 업로드에 실패했습니다.",
            "업로드 실패 원인: " + reason);
    }
    
    public static FileException fileUploadFailed(String reason, Throwable cause) {
        return new FileException("FILE_UPLOAD_FAILED", 
            "파일 업로드에 실패했습니다.",
            cause);
    }
    
    public static FileException invalidFileSize(long actualSize, long maxSize) {
        return new FileException("INVALID_FILE_SIZE", 
            String.format("파일 크기는 %dMB 이하여야 합니다.", maxSize / (1024 * 1024)),
            String.format("파일 크기 초과: %d bytes > %d bytes", actualSize, maxSize));
    }
    
    public static FileException fileNameConflict() {
        return new FileException("FILE_NAME_CONFLICT", 
            "파일명 충돌이 발생했습니다. 다시 시도해주세요.");
    }
    
    public static FileException noFileExtension() {
        return new FileException("NO_FILE_EXTENSION", "파일 확장자가 없습니다.");
    }
    
    public static FileException fileProcessingError(String operation, Throwable cause) {
        return new FileException("FILE_PROCESSING_ERROR", 
            "파일 처리 중 오류가 발생했습니다.",
            cause);
    }
}