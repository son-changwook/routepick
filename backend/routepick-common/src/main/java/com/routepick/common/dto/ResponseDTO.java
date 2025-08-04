package com.routepick.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 응답을 위한 기본 DTO 클래스
 * 모든 API 응답에서 공통적으로 사용되는 메시지와 데이터를 포함합니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDTO<T> {

    /** API 응답 성공 여부 */
    private boolean success;
    
    /** API 응답 데이터 */
    private T data;
    
    /** API 응답 메시지 */
    private String message;
    
    /**
     * 성공 응답 생성
     */
    public static <T> ResponseDTO<T> success(T data, String message) {
        return new ResponseDTO<>(true, data, message);
    }
    
    /**
     * 성공 응답 생성 (메시지 없음)
     */
    public static <T> ResponseDTO<T> success(T data) {
        return new ResponseDTO<>(true, data, "성공");
    }
    
    /**
     * 에러 응답 생성
     */
    public static <T> ResponseDTO<T> error(String message) {
        return new ResponseDTO<>(false, null, message);
    }
}