package com.routepick.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * API 모듈 전용 Redis 설정
 * 
 * 조건부 설정으로 API 모듈에서만 활성화됩니다.
 * 다른 모듈과의 빈 충돌을 방지합니다.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.application.name", havingValue = "routepick-api")
public class ApiRedisConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Value("${spring.redis.password}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int database;

    /**
     * API 모듈 전용 Redis 연결 팩토리
     */
    @Bean("apiRedisConnectionFactory")
    @Primary
    public RedisConnectionFactory apiRedisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setPassword(redisPassword);
        config.setDatabase(database);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        
        log.info("API 모듈 Redis 연결 설정 완료 - Host: {}, Port: {}, Database: {}", 
            redisHost, redisPort, database);
        
        return factory;
    }

    /**
     * API 모듈 전용 Redis Template (JSON 직렬화)
     */
    @Bean("apiRedisTemplate")
    @Primary
    public RedisTemplate<String, Object> apiRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 보안 강화된 JSON 직렬화 설정
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.findAndRegisterModules();
        
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer();

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        
        log.info("API 모듈 RedisTemplate 설정 완료");
        
        return template;
    }

    /**
     * API 모듈 전용 String Redis Template
     */
    @Bean("apiStringRedisTemplate")
    public RedisTemplate<String, String> apiStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        
        log.info("API 모듈 StringRedisTemplate 설정 완료");
        
        return template;
    }

    /**
     * 표준 이름 호환성 (Spring Boot 자동 설정 호환)
     */
    @Bean("redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        return apiRedisTemplate(connectionFactory);
    }

    @Bean("stringRedisTemplate")
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return apiStringRedisTemplate(connectionFactory);
    }
}
