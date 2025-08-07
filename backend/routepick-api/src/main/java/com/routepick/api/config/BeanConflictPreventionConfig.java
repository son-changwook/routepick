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
 * 필요한 경우에만 자동 설정을 제외하고 커스텀 설정을 사용합니다.
 * 
 * ⚠️ 향후 확장 시 주의사항:
 * 1. 새로운 빈 추가 시: 고유한 이름 사용 (예: routepickXXXBean)
 * 2. 자동 설정 추가 시: 이 클래스에 추가
 * 3. 의존성 주입 시: @Qualifier 임시 방편 사용이 아닌 근본적인 해결 접근이 필요함
 * 
 * ⚠️ userName 관련 주의사항:
 * 1. Spring Security 컨벤션: getUsername() = userId (String)
 * 2. 실제 사용자 정보: getUserName() = 실제 사용자 이름
 * 3. 혼동 금지: getUsername() ≠ getUserName()
 * 4. JWT 토큰: sub = userId, userName = 실제 사용자 이름
 */
@Slf4j
@Configuration
@EnableAutoConfiguration(exclude = {
    RedisAutoConfiguration.class,        // Redis 자동 설정 제외 (커스텀 RedisConfig 사용)
    DataSourceAutoConfiguration.class,   // DataSource 자동 설정 제외 (필요시)
    HibernateJpaAutoConfiguration.class // JPA 자동 설정 제외 (필요시)
})
public class BeanConflictPreventionConfig {
    
    public BeanConflictPreventionConfig() {
        log.info("빈 중복 방지 설정이 활성화되었습니다.");
        log.info("제외된 자동 설정: Redis, DataSource, HibernateJPA");
    }
    
    /**
     * 향후 확장 시 빈 이름 규칙
     * 
     * 1. Redis 관련: routepickRedisXXX
     * 2. Security 관련: routepickSecurityXXX  
     * 3. Database 관련: routepickDbXXX
     * 4. Cache 관련: routepickCacheXXX
     * 5. Notification 관련: routepickNotificationXXX
     * 6. File 관련: routepickFileXXX
     * 7. Email 관련: routepickEmailXXX
     * 8. SMS 관련: routepickSmsXXX
     * 9. Payment 관련: routepickPaymentXXX
     * 10. Analytics 관련: routepickAnalyticsXXX
     */
} 