package com.routepick.api.dto.auth;

import lombok.Builder;
import lombok.Getter;

/**
 * 로그인 응답 DTO
 * 로그인 성공 시 발급된 토큰과 사용자 정보를 포함합니다.
 */
@Getter
@Builder
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UserInfo userInfo;
    
    @Getter
    @Builder
    public static class UserInfo {
        private Long userId;
        private String email;
        private String userName; // 사용자 실명
        private String nickName; // 사용자 닉네임
        private String profileImageUrl;
    }
}
