package com.routepick.common.domain.token;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiToken {
    
    private Long tokenId;
    private Long userId;
    private String token;
    private TokenType tokenType;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Boolean isRevoked;
    
    public enum TokenType {
        ACCESS,
        REFRESH,
        RESET_PASSWORD,
        EMAIL_VERIFICATION
    }
} 