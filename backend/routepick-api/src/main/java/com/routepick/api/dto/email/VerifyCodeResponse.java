package com.routepick.api.dto.email;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 인증 코드 검증 응답 DTO
 */
@Getter
@Builder
public class VerifyCodeResponse {
    private String message;
    private String verifiedEmail;
    private String registrationToken;
    private LocalDateTime tokenExpiresAt;
}
