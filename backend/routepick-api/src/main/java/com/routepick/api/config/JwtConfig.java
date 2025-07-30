package com.routepick.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    
    private String secretKey = "routepick-secret-key-2024-very-long-and-secure-key-for-jwt-signing";
    private long accessTokenExpiration = 3600; // 1시간 (초)
    private long refreshTokenExpiration = 2592000; // 30일 (초)
    private String issuer = "routepick-api";
} 