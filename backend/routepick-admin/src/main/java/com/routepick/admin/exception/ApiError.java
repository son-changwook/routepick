package com.routepick.admin.exception;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 관리자 애플리케이션의 예외 처리 시 반환할 오류 응답 형식을 정의하는 레코드.
 * 
 * @param path       오류가 발생한 경로
 * @param message    오류 메시지
 * @param statusCode HTTP 상태 코드
 * @param errorCode  오류를 식별하기 위한 코드
 * @param timestamp  오류 발생 시간 (ISO-8601 형식)
 * @param details    상세 오류 정보 (개발 환경에서만 사용)
 */
public record ApiError(
        String path,
        String message,
        int statusCode,
        String errorCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") LocalDateTime timestamp,
        String details) {
    // 기본 생성자 추가
    public ApiError(String path, String message, int statusCode, String errorCode, LocalDateTime timestamp) {
        this(path, message, statusCode, errorCode, timestamp, null);
    }
}

// 1. ApiError.java
// 예외 처리 시 반환할 오류 응답 형식을 정의하는 레코드.
// path: 오류가 발생한 경로.
// message: 오류 메시지.
// statusCode: HTTP 상태 코드.
// errorCode: 오류를 식별하기 위한 코드.
// timestamp: 오류 발생 시간 (ISO-8601 형식).
// details: 상세 오류 정보 (개발 환경에서만 사용).