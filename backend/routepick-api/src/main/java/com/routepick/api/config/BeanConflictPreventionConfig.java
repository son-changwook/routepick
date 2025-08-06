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
 */
@Slf4j
@Configuration
@EnableAutoConfiguration(exclude = {
    RedisAutoConfiguration.class,        // Redis 자동 설정 제외
    DataSourceAutoConfiguration.class,   // DataSource 자동 설정 제외 (필요시)
    HibernateJpaAutoConfiguration.class // JPA 자동 설정 제외 (필요시)
})
public class BeanConflictPreventionConfig {
    
    public BeanConflictPreventionConfig() {
        log.info("빈 중복 방지 설정이 활성화되었습니다.");
    }
} 