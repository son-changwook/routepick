package com.routepick.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 표준화된 API 응답 구조
 * 모든 API 엔드포인트에서 일관된 응답 형식을 제공합니다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    /**
     * 응답 성공 여부
     */
    private boolean success;
    
    /**
     * HTTP 상태 코드
     */
    private int code;
    
    /**
     * 응답 메시지
     */
    private String message;
    
    /**
     * 응답 데이터
     */
    private T data;
    
    /**
     * 응답 시간
     */
    private LocalDateTime timestamp;
    
    /**
     * 에러 정보 (실패 시에만 포함)
     */
    private ErrorInfo error;
    
    /**
     * 성공 응답 생성
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(200)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 성공 응답 생성 (데이터 없음)
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(200)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 에러 응답 생성
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .error(ErrorInfo.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }
    
    /**
     * 에러 응답 생성 (상세 정보 포함)
     */
    public static <T> ApiResponse<T> error(int code, String message, String details) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .error(ErrorInfo.builder()
                        .code(code)
                        .message(message)
                        .details(details)
                        .build())
                .build();
    }
    
    /**
     * 에러 정보 내부 클래스
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorInfo {
        private int code;
        private String message;
        private String details;
        private String field;
    }
}