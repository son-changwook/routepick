package com.routepick.api.dto.email;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 이메일 인증 응답 DTO
 */
@Getter
@Builder
public class EmailVerificationResponse {
    private String message;
    private String verificationCode; // 개발용, 실제로는 제거
    private LocalDateTime expiresAt;
    private String sessionToken; // 임시 세션 토큰
}
