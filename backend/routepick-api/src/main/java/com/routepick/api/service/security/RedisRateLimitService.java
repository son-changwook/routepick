package com.routepick.api.service.security;

import com.routepick.api.util.SecureLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.util.List;

/**
 * Redis 기반 Rate Limiting 서비스
 * Sliding Window 알고리즘을 사용하여 정확한 Rate Limiting을 제공합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisRateLimitService {

    private final RedisTemplate<String, String> apiStringRedisTemplate;

    // Lua 스크립트를 사용한 원자적 Rate Limiting
    private static final String RATE_LIMIT_SCRIPT = """
        local key = KEYS[1]
        local window = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])
        local current_time = tonumber(ARGV[3])
        
        -- 현재 시간 기준으로 윈도우 시작 시간 계산
        local window_start = current_time - window
        
        -- 만료된 요청들 제거
        redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)
        
        -- 현재 윈도우 내 요청 수 확인
        local current_requests = redis.call('ZCARD', key)
        
        if current_requests < limit then
            -- 현재 요청 추가
            redis.call('ZADD', key, current_time, current_time)
            -- TTL 설정 (윈도우 크기의 2배로 설정하여 안전하게 정리)
            redis.call('EXPIRE', key, window * 2)
            return {1, limit - current_requests - 1}
        else
            return {0, 0}
        end
        """;

    private DefaultRedisScript<List<Long>> rateLimitScript;

    @SuppressWarnings("unchecked")
    @PostConstruct
    public void init() {
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setScriptText(RATE_LIMIT_SCRIPT);
        this.rateLimitScript.setResultType((Class<List<Long>>) (Class<?>) List.class);
    }

    /**
     * IP 기반 Rate Limiting
     * @param ipAddress 클라이언트 IP 주소
     * @return 요청 허용 여부
     */
    public boolean tryConsumeByIp(String ipAddress) {
        return tryConsume("rate_limit:ip:" + ipAddress, 2000, 60); // 1분에 2000회 (개발 환경 완화)
    }

    /**
     * 이메일 기반 Rate Limiting  
     * @param email 이메일 주소
     * @return 요청 허용 여부
     */
    public boolean tryConsumeByEmail(String email) {
        String maskedEmail = SecureLogger.maskEmail(email);
        String key = "rate_limit:email:" + maskedEmail;
        return tryConsume(key, 200, 3600); // 1시간에 200회 (개발 환경 완화)
    }

    /**
     * 전역 Rate Limiting
     * @return 요청 허용 여부
     */
    public boolean tryConsumeGlobal() {
        return tryConsume("rate_limit:global", 1000, 60); // 1분에 1000회
    }

    /**
     * 사용자별 Rate Limiting
     * @param userId 사용자 ID
     * @return 요청 허용 여부
     */
    public boolean tryConsumeByUser(String userId) {
        return tryConsume("rate_limit:user:" + userId, 200, 60); // 1분에 200회
    }

    /**
     * API 엔드포인트별 Rate Limiting
     * @param endpoint API 엔드포인트
     * @param ipAddress 클라이언트 IP
     * @return 요청 허용 여부
     */
    public boolean tryConsumeByEndpoint(String endpoint, String ipAddress) {
        String maskedIp = SecureLogger.maskIpAddress(ipAddress);
        String key = "rate_limit:endpoint:" + endpoint + ":" + maskedIp;
        
        // 엔드포인트별 다른 제한 설정
        switch (endpoint) {
            case "/api/auth/login":
                return tryConsume(key, 10, 300); // 5분에 10회
            case "/api/auth/signup":
                return tryConsume(key, 20, 3600); // 1시간에 20회 (개발 환경 완화)
            case "/api/auth/email/verification":
                return tryConsume(key, 5, 3600); // 1시간에 5회
            default:
                return tryConsume(key, 50, 60); // 1분에 50회
        }
    }

    /**
     * 실제 Rate Limiting 로직 (Sliding Window 알고리즘)
     * @param key Redis 키
     * @param limit 제한 횟수
     * @param windowSeconds 윈도우 크기 (초)
     * @return 요청 허용 여부
     */
    private boolean tryConsume(String key, int limit, int windowSeconds) {
        try {
            long currentTime = System.currentTimeMillis();
            
            List<Long> result = apiStringRedisTemplate.execute(
                rateLimitScript,
                List.of(key),
                String.valueOf(windowSeconds * 1000), // 밀리초로 변환
                String.valueOf(limit),
                String.valueOf(currentTime)
            );

            if (result != null && result.size() >= 2) {
                boolean allowed = result.get(0) == 1L;
                // long remainingRequests = result.get(1); // 사용하지 않음

                if (!allowed) {
                    log.warn("Rate limit exceeded for key: {}, limit: {}, window: {}s", 
                        key, limit, windowSeconds);
                    
                    // 보안 이벤트 로깅
                    SecureLogger.logSecurityEvent(
                        "Rate limit exceeded - key: {}, limit: {}, window: {}s",
                        maskSensitiveKey(key), limit, windowSeconds
                    );
                }

                return allowed;
            }

            // 스크립트 실행 실패 시 기본적으로 허용 (서비스 중단 방지)
            log.error("Rate limit script execution failed for key: {}", key);
            return true;

        } catch (Exception e) {
            log.error("Rate limiting error for key: {}, error: {}", key, e.getMessage(), e);
            // Redis 오류 시 기본적으로 허용 (서비스 중단 방지)
            return true;
        }
    }

    /**
     * Rate Limit 정보 조회
     * @param key Redis 키
     * @param windowSeconds 윈도우 크기 (초)
     * @return 현재 윈도우 내 요청 수
     */
    public long getCurrentRequestCount(String key, int windowSeconds) {
        try {
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - (windowSeconds * 1000L);

            // 만료된 요청들 제거
            apiStringRedisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
            
            // 현재 윈도우 내 요청 수 반환
            Long count = apiStringRedisTemplate.opsForZSet().count(key, windowStart, currentTime);
            return count != null ? count : 0;

        } catch (Exception e) {
            log.error("Failed to get current request count for key: {}", key, e);
            return 0;
        }
    }

    /**
     * Rate Limit 초기화 (관리자 기능)
     * @param key Redis 키
     */
    public void resetRateLimit(String key) {
        try {
            apiStringRedisTemplate.delete(key);
            log.info("Rate limit reset for key: {}", maskSensitiveKey(key));
        } catch (Exception e) {
            log.error("Failed to reset rate limit for key: {}", key, e);
        }
    }

    /**
     * 모든 Rate Limit 정보 정리 (스케줄링 작업용)
     */
    public void cleanupExpiredRateLimits() {
        try {
            // 패턴 매칭으로 모든 rate_limit 키 찾기
            String pattern = "rate_limit:*";
            apiStringRedisTemplate.keys(pattern).forEach(key -> {
                try {
                    // TTL이 설정되지 않은 키들 정리
                    Long ttl = apiStringRedisTemplate.getExpire(key);
                    if (ttl != null && ttl == -1) {
                        apiStringRedisTemplate.expire(key, Duration.ofHours(2));
                    }
                } catch (Exception e) {
                    log.warn("Failed to cleanup rate limit key: {}", key, e);
                }
            });
            
            log.debug("Rate limit cleanup completed");
            
        } catch (Exception e) {
            log.error("Rate limit cleanup failed", e);
        }
    }

    /**
     * 민감한 정보가 포함된 키를 마스킹
     * @param key Redis 키
     * @return 마스킹된 키
     */
    private String maskSensitiveKey(String key) {
        if (key.contains("email:")) {
            String[] parts = key.split(":");
            if (parts.length >= 3) {
                return parts[0] + ":" + parts[1] + ":***";
            }
        }
        if (key.contains("ip:")) {
            String[] parts = key.split(":");
            if (parts.length >= 3) {
                return parts[0] + ":" + parts[1] + ":***";
            }
        }
        return key;
    }
}