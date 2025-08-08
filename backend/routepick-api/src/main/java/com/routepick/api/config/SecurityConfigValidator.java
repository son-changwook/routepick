package com.routepick.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * 보안 설정 검증 클래스
 * 애플리케이션 시작 시 필수 보안 환경변수들을 검증합니다.
 */
@Slf4j
@Component
public class SecurityConfigValidator {
    
    /**
     * 애플리케이션 시작 시 보안 설정 검증
     */
    @PostConstruct
    public void validateSecurityConfig() {
        log.info("보안 설정 검증을 시작합니다...");
        
        validateJwtSecret();
        validateDatabaseCredentials();
        validateEmailCredentials();
        
        // JWT 보안 검증
        validateJwtSecurity();
        
        log.info("보안 설정 검증이 완료되었습니다.");
    }
    
    /**
     * JWT Secret 환경변수 검증
     */
    private void validateJwtSecret() {
        String jwtSecret = System.getenv("JWT_SECRET");
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        
        if ("prod".equals(profile) || "production".equals(profile)) {
            if (jwtSecret == null || jwtSecret.isEmpty()) {
                throw new SecurityException(
                    "프로덕션 환경에서 JWT_SECRET 환경변수가 설정되지 않았습니다. " +
                    "애플리케이션을 시작할 수 없습니다."
                );
            }
            
            if (jwtSecret.length() < 32) {
                throw new SecurityException(
                    "프로덕션 환경의 JWT_SECRET은 최소 32자 이상이어야 합니다. " +
                    "현재 길이: " + jwtSecret.length() + "자"
                );
            }
            
            log.info("JWT_SECRET 환경변수가 올바르게 설정되었습니다.");
        } else {
            if (jwtSecret == null || jwtSecret.isEmpty()) {
                log.warn("개발 환경에서 JWT_SECRET 환경변수가 설정되지 않았습니다. 기본값을 사용합니다.");
            } else {
                log.info("JWT_SECRET 환경변수가 설정되었습니다.");
            }
        }
    }
    
    /**
     * 데이터베이스 자격증명 검증
     */
    private void validateDatabaseCredentials() {
        String dbPassword = System.getenv("DB_PASSWORD");
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        
        if ("prod".equals(profile) || "production".equals(profile)) {
            if (dbPassword == null || dbPassword.isEmpty()) {
                throw new SecurityException(
                    "프로덕션 환경에서 DB_PASSWORD 환경변수가 설정되지 않았습니다. " +
                    "애플리케이션을 시작할 수 없습니다."
                );
            }
            
            log.info("DB_PASSWORD 환경변수가 올바르게 설정되었습니다.");
        } else {
            if (dbPassword == null || dbPassword.isEmpty()) {
                log.warn("개발 환경에서 DB_PASSWORD 환경변수가 설정되지 않았습니다. 기본값을 사용합니다.");
            } else {
                log.info("DB_PASSWORD 환경변수가 설정되었습니다.");
            }
        }
    }
    
    /**
     * 이메일 자격증명 검증
     */
    private void validateEmailCredentials() {
        String mailPassword = System.getenv("MAIL_PASSWORD");
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        
        if ("prod".equals(profile) || "production".equals(profile)) {
            if (mailPassword == null || mailPassword.isEmpty()) {
                throw new SecurityException(
                    "프로덕션 환경에서 MAIL_PASSWORD 환경변수가 설정되지 않았습니다. " +
                    "애플리케이션을 시작할 수 없습니다."
                );
            }
            
            log.info("MAIL_PASSWORD 환경변수가 올바르게 설정되었습니다.");
        } else {
            if (mailPassword == null || mailPassword.isEmpty()) {
                log.warn("개발 환경에서 MAIL_PASSWORD 환경변수가 설정되지 않았습니다. 기본값을 사용합니다.");
            } else {
                log.info("MAIL_PASSWORD 환경변수가 설정되었습니다.");
            }
        }
    }

    /**
     * JWT 보안 설정 검증
     */
    private void validateJwtSecurity() {
        String jwtSecret = jwtConfig.getSecretKey();
        
        // 시크릿 키 길이 검증
        if (jwtSecret.length() < 32) {
            log.error("❌ JWT_SECRET이 너무 짧습니다. 최소 32자 이상이 필요합니다.");
            log.error("현재 길이: {}자", jwtSecret.length());
            throw new SecurityException("JWT_SECRET이 너무 짧습니다.");
        }
        
        // 시크릿 키 복잡성 검증
        if (!jwtSecret.matches(".*[A-Z].*") || 
            !jwtSecret.matches(".*[a-z].*") || 
            !jwtSecret.matches(".*[0-9].*") || 
            !jwtSecret.matches(".*[^A-Za-z0-9].*")) {
            log.error("❌ JWT_SECRET이 충분히 복잡하지 않습니다.");
            log.error("영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.");
            throw new SecurityException("JWT_SECRET이 충분히 복잡하지 않습니다.");
        }
        
        // 토큰 만료 시간 검증
        if (jwtConfig.getExpiration() > 3600000) { // 1시간
            log.warn("⚠️ JWT_EXPIRATION이 너무 깁니다. 보안을 위해 30분 이하를 권장합니다.");
        }
        
        if (jwtConfig.getRefreshExpiration() > 604800000) { // 7일
            log.warn("⚠️ JWT_REFRESH_EXPIRATION이 너무 깁니다. 보안을 위해 7일 이하를 권장합니다.");
        }
        
        log.info("✅ JWT 보안 설정이 적절합니다.");
    }
} 