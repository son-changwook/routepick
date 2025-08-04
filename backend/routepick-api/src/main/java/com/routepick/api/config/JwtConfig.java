package com.routepick.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * JWT 설정 클래스
 * JWT 토큰 생성 및 검증에 필요한 설정 정보를 관리합니다.
 * 환경변수 필수화 검증을 포함합니다.
 */
@Data
@Component
@ConfigurationProperties(prefix = "api.jwt")
public class JwtConfig {
    
    private String secretKey = "routepick-secret-key-2024-very-long-and-secure-key-for-jwt-signing";
    private long expiration = 3600000; // 1시간 (밀리초)
    private long refreshExpiration = 2592000000L; // 30일 (밀리초)
    private String issuer = "routepick-api";
    
    /**
     * JWT Secret 환경변수 필수화 검증
     * 애플리케이션 시작 시 실행되어 보안 설정을 검증합니다.
     */
    @PostConstruct
    public void validateJwtSecret() {
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        boolean isProduction = "prod".equals(profile) || "production".equals(profile);
        
        if (isProduction) {
            // 프로덕션 환경에서는 환경변수 필수
            if (secretKey == null || secretKey.isEmpty() || 
                secretKey.equals("routepick-secret-key-2024-very-long-and-secure-key-for-jwt-signing")) {
                throw new IllegalStateException(
                    "프로덕션 환경에서 JWT_SECRET 환경변수가 설정되지 않았습니다. " +
                    "환경변수 JWT_SECRET을 설정한 후 애플리케이션을 다시 시작하세요."
                );
            }
            
            // 최소 키 길이 검증 (256비트 = 32바이트)
            if (secretKey.length() < 32) {
                throw new IllegalStateException(
                    "프로덕션 환경의 JWT_SECRET은 최소 32자 이상이어야 합니다. " +
                    "현재 길이: " + secretKey.length() + "자"
                );
            }
            
            // 키 복잡성 검증
            if (!isComplexSecret(secretKey)) {
                throw new IllegalStateException(
                    "프로덕션 환경의 JWT_SECRET은 영문, 숫자, 특수문자를 포함해야 합니다."
                );
            }
        } else {
            // 개발 환경에서는 경고만 출력
            if (secretKey.equals("routepick-secret-key-2024-very-long-and-secure-key-for-jwt-signing")) {
                System.out.println("⚠️  경고: 개발 환경에서 기본 JWT_SECRET을 사용하고 있습니다.");
                System.out.println("   프로덕션 환경에서는 반드시 강력한 JWT_SECRET을 설정하세요.");
            }
        }
    }
    
    /**
     * JWT Secret 복잡성 검증
     * 영문, 숫자, 특수문자 포함 여부를 확인합니다.
     */
    private boolean isComplexSecret(String secret) {
        return secret.matches(".*[a-zA-Z].*") && 
               secret.matches(".*[0-9].*") && 
               secret.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");
    }
} 