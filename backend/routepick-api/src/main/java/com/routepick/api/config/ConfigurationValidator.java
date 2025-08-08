package com.routepick.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 애플리케이션 설정 검증 클래스
 * 프로덕션 환경에서 필수 설정들이 올바르게 구성되었는지 검증합니다.
 */
@Slf4j
@Component
public class ConfigurationValidator {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${api.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        log.info("설정 검증을 시작합니다... (프로파일: {})", activeProfile);

        if ("prod".equals(activeProfile) || "production".equals(activeProfile)) {
            validateProductionConfiguration();
        } else {
            validateDevelopmentConfiguration();
        }

        log.info("설정 검증이 완료되었습니다.");
    }

    /**
     * 프로덕션 환경 설정 검증
     */
    private void validateProductionConfiguration() {
        log.info("프로덕션 환경 설정을 검증합니다...");

        // JWT Secret 검증
        if (!StringUtils.hasText(jwtSecret) || jwtSecret.equals("dev-jwt-secret-key-2024")) {
            throw new IllegalStateException(
                "프로덕션 환경에서는 JWT_SECRET 환경변수를 반드시 설정해야 합니다."
            );
        }

        // 데이터베이스 비밀번호 검증
        if (!StringUtils.hasText(dbPassword) || dbPassword.equals("routepick")) {
            throw new IllegalStateException(
                "프로덕션 환경에서는 DATABASE_PASSWORD 환경변수를 반드시 설정해야 합니다."
            );
        }

        // Redis 비밀번호 검증
        if (!StringUtils.hasText(redisPassword) || redisPassword.equals("routepick-redis-password")) {
            throw new IllegalStateException(
                "프로덕션 환경에서는 REDIS_PASSWORD 환경변수를 반드시 설정해야 합니다."
            );
        }

        // 이메일 비밀번호 검증 (선택사항)
        if (!StringUtils.hasText(mailPassword)) {
            log.warn("프로덕션 환경에서 MAIL_PASSWORD가 설정되지 않았습니다. 이메일 기능이 제한될 수 있습니다.");
        }

        log.info("프로덕션 환경 설정 검증이 완료되었습니다.");
    }

    /**
     * 개발 환경 설정 검증
     */
    private void validateDevelopmentConfiguration() {
        log.info("개발 환경 설정을 검증합니다...");

        // 개발 환경에서는 기본값 사용 가능
        if (!StringUtils.hasText(jwtSecret)) {
            log.warn("개발 환경에서 JWT_SECRET이 설정되지 않았습니다. 기본값을 사용합니다.");
        }

        if (!StringUtils.hasText(dbPassword)) {
            log.warn("개발 환경에서 DATABASE_PASSWORD가 설정되지 않았습니다. 기본값을 사용합니다.");
        }

        if (!StringUtils.hasText(redisPassword)) {
            log.warn("개발 환경에서 REDIS_PASSWORD가 설정되지 않았습니다. 기본값을 사용합니다.");
        }

        if (!StringUtils.hasText(mailPassword)) {
            log.warn("개발 환경에서 MAIL_PASSWORD가 설정되지 않았습니다. 이메일 기능이 제한될 수 있습니다.");
        }

        log.info("개발 환경 설정 검증이 완료되었습니다.");
    }

    /**
     * 보안 설정 검증
     */
    public void validateSecurityConfiguration() {
        log.info("보안 설정을 검증합니다...");

        // JWT Secret 강도 검증
        if (StringUtils.hasText(jwtSecret) && jwtSecret.length() < 32) {
            log.warn("JWT_SECRET이 32자 미만입니다. 보안을 위해 더 긴 키를 사용하세요.");
        }

        // 데이터베이스 연결 보안 검증
        if (StringUtils.hasText(dbPassword) && dbPassword.length() < 8) {
            log.warn("데이터베이스 비밀번호가 8자 미만입니다. 보안을 위해 더 긴 비밀번호를 사용하세요.");
        }

        log.info("보안 설정 검증이 완료되었습니다.");
    }
}
