package com.routepick.api.dto.email;

import lombok.Builder;
import lombok.Getter;

/**
 * 이메일 중복 확인 응답 DTO
 */
@Getter
@Builder
public class EmailCheckResponse {
    private boolean available;
    private String message;
    private boolean verificationRequired;
}
