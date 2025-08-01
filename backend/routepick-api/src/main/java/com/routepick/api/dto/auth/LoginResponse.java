package com.routepick.api.dto.auth;

import lombok.Builder;
import lombok.Getter;

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
        private String userName;
        private String profileImageUrl;
    }
}
