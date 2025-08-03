package com.routepick.common.domain.session;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SignupSession {
    private String sessionId;
    private String email;
    private String verificationCode;
    private String registrationToken;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isVerified;
}
