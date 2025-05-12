package com.routepick.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * API 응답을 위한 기본 DTO 클래스
 * 모든 API 응답에서 공통적으로 사용되는 메시지를 포함합니다.
 */
@Data
@AllArgsConstructor
public class ResponseDTO {

    /** API 응답 메시지 */
    private String message;
}