package com.routepick.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 토큰 갱신 요청 DTO
 * 리프레시 토큰을 입력받아 토큰 갱신 요청을 처리합니다.
 */
@Getter
@Setter
public class TokenRefreshRequest {
    
    @NotBlank(message = "리프레시 토큰은 필수입니다.")
    private String refreshToken;
} 