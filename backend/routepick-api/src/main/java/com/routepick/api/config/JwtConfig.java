package com.routepick.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 설정 클래스
 * JWT 토큰 생성 및 검증에 필요한 설정 정보를 관리합니다.
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    
    private String secretKey = "routepick-secret-key-2024-very-long-and-secure-key-for-jwt-signing";
    private long accessTokenExpiration = 3600; // 1시간 (초)
    private long refreshTokenExpiration = 2592000; // 30일 (초)
    private String issuer = "routepick-api";
} 