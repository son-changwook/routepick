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
} 