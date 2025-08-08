package com.routepick.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * 빈 중복 방지 설정
 * 
 * Spring Boot 자동 설정으로 인한 빈 중복을 방지합니다.
 * 모듈별 독립적인 설정을 위해 자동 설정을 제외하고 커스텀 설정을 사용합니다.
 * 
 * ✅ 근본적 해결 완료:
 * 1. 모듈별 독립적인 설정 클래스 사용 (ApiRedisConfig, AdminRedisConfig)
 * 2. 조건부 빈 생성 (@ConditionalOnProperty)
 * 3. 명확한 빈 이름 규칙 (apiXXX, adminXXX)
 * 4. 모듈별 Redis 데이터베이스 분리 (API: DB 0, Admin: DB 1)
 * 
 * ⚠️ userName과 nickName 관련 주의사항:
 * 1. Spring Security 컨벤션: getUsername() = userId (String)
 * 2. 실제 사용자 정보: getUserName() = 사용자 실명, getNickName() = 사용자 닉네임
 * 3. 혼동 금지: getUsername() ≠ getUserName() ≠ getNickName()
 * 4. JWT 토큰: sub = userId, userName = 사용자 실명, nickName = 사용자 닉네임
 */
@Slf4j
@Configuration
@EnableAutoConfiguration(exclude = {
    RedisAutoConfiguration.class        // Redis 자동 설정 제외 (커스텀 RedisConfig 사용)
})
public class BeanConflictPreventionConfig {
    
    public BeanConflictPreventionConfig() {
        log.info("빈 중복 방지 설정이 활성화되었습니다.");
        log.info("제외된 자동 설정: Redis");
    }
    
    /**
     * 모듈별 빈 이름 규칙 (근본적 해결 적용)
     * 
     * 1. API 모듈: apiXXX (apiRedisTemplate, apiStringRedisTemplate)
     * 2. Admin 모듈: adminXXX (adminRedisTemplate, adminStringRedisTemplate)
     * 3. 향후 모듈: {모듈명}XXX (mobileRedisTemplate, webRedisTemplate)
     * 
     * 확장 시 주의사항:
     * - 새로운 모듈 추가 시: @ConditionalOnProperty 사용
     * - 모듈별 독립적인 설정 클래스 생성
     * - Redis 데이터베이스 분리 (DB 0, 1, 2...)
     */
} 