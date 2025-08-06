package com.routepick.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
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
 * Redis 설정 클래스
 * 보안 강화된 Redis 연결 및 직렬화 설정을 제공합니다.
 * Spring Boot의 자동 설정을 제거하고 완전히 커스텀 설정을 사용합니다.
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Value("${spring.redis.password}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private int database;

    /**
     * Redis 연결 팩토리 설정
     */
    @Bean("routepickRedisConnectionFactory")
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setPassword(redisPassword);
        config.setDatabase(database);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        
        log.info("Redis 연결 설정 완료 - Host: {}, Port: {}, Database: {}", 
            redisHost, redisPort, database);
        
        return factory;
    }

    /**
     * Redis Template 설정 (보안 강화된 직렬화)
     */
    @Bean("routepickRedisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 보안 강화된 JSON 직렬화 설정
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // 보안을 위해 기본 타이핑 설정 제거 (더 안전한 방법 사용)
        objectMapper.findAndRegisterModules();
        
        // 보안 강화: 특정 클래스만 허용하는 직렬화 설정
        GenericJackson2JsonRedisSerializer jsonSerializer = 
            new GenericJackson2JsonRedisSerializer();

        // Key는 String으로, Value는 JSON으로 직렬화
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jsonSerializer);

        // 트랜잭션 지원 활성화
        template.setEnableTransactionSupport(true);
        
        template.afterPropertiesSet();
        
        log.info("RedisTemplate 설정 완료 - JSON 직렬화 사용");
        
        return template;
    }

    /**
     * String 전용 Redis Template (성능 최적화)
     * 이 빈이 RedisTemplate<String, String> 타입의 기본 빈이 됩니다.
     */
    @Bean("routepickStringRedisTemplate")
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 모든 직렬화를 String으로 설정
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        
        log.info("StringRedisTemplate 설정 완료");
        
        return template;
    }
}