package com.routepick.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 표준 API 응답 래퍼 클래스
 * 모든 API 응답은 이 구조를 따라야 합니다.
 * 
 * @param <T> 응답 데이터의 타입
 */
@Getter
@Builder
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    /**
     * 응답 코드 (HTTP 상태코드와 동일하거나 비즈니스 로직 코드)
     */
    private final int code;
    
    /**
     * 응답 메시지 (성공/실패 메시지)
     */
    private final String message;
    
    /**
     * 실제 응답 데이터 (성공 시에만 포함)
     */
    private final T data;
    
    /**
     * 에러 세부 정보 (실패 시에만 포함)
     */
    private final ErrorDetail error;
    
    /**
     * 타임스탬프
     */
    @Builder.Default
    private final long timestamp = System.currentTimeMillis();
    
    /**
     * 성공 응답 생성 (데이터 포함)
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("성공")
                .data(data)
                .build();
    }
    
    /**
     * 성공 응답 생성 (메시지 커스터마이징)
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .build();
    }
    
    /**
     * 성공 응답 생성 (데이터 없음)
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .build();
    }
    
    /**
     * 실패 응답 생성
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .build();
    }
    
    /**
     * 실패 응답 생성 (에러 세부 정보 포함)
     */
    public static <T> ApiResponse<T> error(int code, String message, ErrorDetail error) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .error(error)
                .build();
    }
    
    /**
     * 에러 세부 정보 클래스
     */
    @Getter
    @Builder
    @RequiredArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        /**
         * 에러 필드명 (검증 오류 시)
         */
        private final String field;
        
        /**
         * 에러 상세 메시지
         */
        private final String detail;
        
        /**
         * 에러 코드 (비즈니스 로직용)
         */
        private final String errorCode;
        
        /**
         * 추적 ID (디버깅용)
         */
        private final String traceId;
        
        public static ErrorDetail of(String detail) {
            return ErrorDetail.builder()
                    .detail(detail)
                    .build();
        }
        
        public static ErrorDetail of(String field, String detail) {
            return ErrorDetail.builder()
                    .field(field)
                    .detail(detail)
                    .build();
        }
        
        public static ErrorDetail of(String field, String detail, String errorCode) {
            return ErrorDetail.builder()
                    .field(field)
                    .detail(detail)
                    .errorCode(errorCode)
                    .build();
        }
    }
}