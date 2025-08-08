package com.routepick.api.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 토큰 블랙리스트 서비스
 * 무효화된 토큰을 Redis에 저장하여 빠른 검증을 제공합니다.
 */
@Slf4j
@Service
public class TokenBlacklistService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    
    public TokenBlacklistService(@Qualifier("apiStringRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    /**
     * 토큰을 블랙리스트에 추가
     * @param token 무효화할 토큰
     * @param expirationHours 토큰 만료 시간 (시간)
     */
    public void addToBlacklist(String token, long expirationHours) {
        try {
            String key = BLACKLIST_PREFIX + token;
            // 토큰의 만료 시간만큼 블랙리스트에 저장
            redisTemplate.opsForValue().set(key, "revoked", expirationHours, TimeUnit.HOURS);
            log.debug("토큰 블랙리스트 추가: {}", token.substring(0, 10) + "***");
            
        } catch (Exception e) {
            log.error("토큰 블랙리스트 추가 실패: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 토큰이 블랙리스트에 있는지 확인
     * @param token 확인할 토큰
     * @return 블랙리스트에 있으면 true
     */
    public boolean isBlacklisted(String token) {
        try {
            String key = BLACKLIST_PREFIX + token;
            String value = redisTemplate.opsForValue().get(key);
            return value != null;
            
        } catch (Exception e) {
            log.error("토큰 블랙리스트 확인 실패: {}", e.getMessage(), e);
            return false; // Redis 오류 시 안전하게 false 반환
        }
    }
    
    /**
     * 블랙리스트에서 토큰 제거 (테스트용)
     * @param token 제거할 토큰
     */
    public void removeFromBlacklist(String token) {
        try {
            String key = BLACKLIST_PREFIX + token;
            redisTemplate.delete(key);
            log.debug("토큰 블랙리스트 제거: {}", token.substring(0, 10) + "***");
            
        } catch (Exception e) {
            log.error("토큰 블랙리스트 제거 실패: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 블랙리스트 크기 조회 (모니터링용)
     * @return 블랙리스트에 있는 토큰 개수
     */
    public long getBlacklistSize() {
        try {
            return redisTemplate.keys(BLACKLIST_PREFIX + "*").size();
            
        } catch (Exception e) {
            log.error("블랙리스트 크기 조회 실패: {}", e.getMessage(), e);
            return 0;
        }
    }
} 