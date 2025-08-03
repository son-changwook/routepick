package com.routepick.api.dto.auth;

import lombok.Builder;
import lombok.Getter;

/**
 * 토큰 갱신 응답 DTO
 * 토큰 갱신 성공 시 발급된 토큰과 사용자 정보를 포함합니다.
 */
@Getter
@Builder
public class TokenRefreshResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
} 