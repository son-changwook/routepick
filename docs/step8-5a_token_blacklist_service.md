# 8-5a: TokenBlacklist Service 구현

## 📋 구현 목표
- **토큰 무효화**: 로그아웃/탈퇴 시 JWT 토큰 블랙리스트 관리
- **Redis 저장소**: 분산 환경에서 실시간 토큰 블랙리스트 동기화
- **자동 정리**: 만료된 토큰 자동 정리로 메모리 최적화
- **빠른 검증**: O(1) 시간복잡도로 토큰 블랙리스트 조회

## 🔐 TokenBlacklistService 구현

### TokenBlacklistService.java
```java
package com.routepick.backend.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * JWT 토큰 블랙리스트 관리 서비스
 * - 로그아웃/탈퇴 시 토큰 무효화
 * - Redis 기반 분산 토큰 블랙리스트
 * - 만료 토큰 자동 정리
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    // Redis Key Patterns
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:token:";
    private static final String USER_BLACKLIST_PREFIX = "blacklist:user:";
    private static final String CLEANUP_LOCK_KEY = "blacklist:cleanup:lock";
    
    /**
     * 토큰을 블랙리스트에 추가
     */
    public void addToBlacklist(String token, Long userId, Duration ttl) {
        try {
            String tokenKey = BLACKLIST_KEY_PREFIX + token;
            String userKey = USER_BLACKLIST_PREFIX + userId;
            
            // 1. 개별 토큰 블랙리스트 추가 (TTL 설정)
            redisTemplate.opsForValue().set(tokenKey, "blacklisted", ttl);
            
            // 2. 사용자별 블랙리스트 세트에 추가
            redisTemplate.opsForSet().add(userKey, token);
            redisTemplate.expire(userKey, ttl);
            
            // 3. 통계 증가
            redisTemplate.opsForValue().increment("blacklist:stats:total", 1);
            
            log.info("토큰이 블랙리스트에 추가됨 - userId: {}, token: {}..., ttl: {}", 
                    userId, token.substring(0, 20), ttl.toMinutes());
                    
        } catch (Exception e) {
            log.error("토큰 블랙리스트 추가 실패 - userId: {}, error: {}", userId, e.getMessage());
            throw new RuntimeException("토큰 블랙리스트 추가 실패", e);
        }
    }
    
    /**
     * 토큰이 블랙리스트에 있는지 확인 (O(1) 조회)
     */
    public boolean isTokenBlacklisted(String token) {
        try {
            String tokenKey = BLACKLIST_KEY_PREFIX + token;
            Boolean exists = redisTemplate.hasKey(tokenKey);
            
            if (Boolean.TRUE.equals(exists)) {
                log.debug("블랙리스트된 토큰 탐지: {}...", token.substring(0, 20));
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("토큰 블랙리스트 확인 실패 - token: {}..., error: {}", 
                    token.substring(0, 20), e.getMessage());
            // 보안상 실패 시에는 블랙리스트된 것으로 처리
            return true;
        }
    }
    
    /**
     * 사용자의 모든 토큰을 블랙리스트에 추가 (계정 정지/탈퇴)
     */
    public void blacklistAllUserTokens(Long userId, Duration ttl) {
        try {
            String userKey = USER_BLACKLIST_PREFIX + userId;
            Set<String> userTokens = redisTemplate.opsForSet().members(userKey);
            
            if (userTokens != null && !userTokens.isEmpty()) {
                for (String token : userTokens) {
                    String tokenKey = BLACKLIST_KEY_PREFIX + token;
                    redisTemplate.opsForValue().set(tokenKey, "blacklisted", ttl);
                }
                
                log.info("사용자의 모든 토큰이 블랙리스트됨 - userId: {}, tokenCount: {}", 
                        userId, userTokens.size());
            }
            
            // 사용자별 블랙리스트도 TTL 연장
            redisTemplate.expire(userKey, ttl);
            
        } catch (Exception e) {
            log.error("사용자 토큰 전체 블랙리스트 실패 - userId: {}, error: {}", userId, e.getMessage());
            throw new RuntimeException("사용자 토큰 전체 블랙리스트 실패", e);
        }
    }
    
    /**
     * 만료된 토큰 정리 (스케줄러에서 호출)
     */
    public void cleanupExpiredTokens() {
        String lockKey = CLEANUP_LOCK_KEY;
        Boolean lockAcquired = false;
        
        try {
            // 분산 락으로 중복 정리 방지
            lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", 
                    Duration.ofMinutes(10));
            
            if (!Boolean.TRUE.equals(lockAcquired)) {
                log.debug("다른 인스턴스에서 토큰 정리 진행 중");
                return;
            }
            
            // 블랙리스트 키 패턴으로 만료 토큰 스캔
            Set<String> blacklistKeys = redisTemplate.keys(BLACKLIST_KEY_PREFIX + "*");
            Set<String> userBlacklistKeys = redisTemplate.keys(USER_BLACKLIST_PREFIX + "*");
            
            int cleanedTokens = 0;
            int cleanedUsers = 0;
            
            // 개별 토큰 정리
            if (blacklistKeys != null) {
                for (String key : blacklistKeys) {
                    Boolean exists = redisTemplate.hasKey(key);
                    if (!Boolean.TRUE.equals(exists)) {
                        redisTemplate.delete(key);
                        cleanedTokens++;
                    }
                }
            }
            
            // 사용자별 토큰 세트 정리
            if (userBlacklistKeys != null) {
                for (String key : userBlacklistKeys) {
                    Boolean exists = redisTemplate.hasKey(key);
                    if (!Boolean.TRUE.equals(exists)) {
                        redisTemplate.delete(key);
                        cleanedUsers++;
                    }
                }
            }
            
            log.info("만료된 토큰 정리 완료 - 토큰: {}개, 사용자: {}개", cleanedTokens, cleanedUsers);
            
        } catch (Exception e) {
            log.error("토큰 정리 중 오류 발생: {}", e.getMessage());
        } finally {
            if (Boolean.TRUE.equals(lockAcquired)) {
                redisTemplate.delete(lockKey);
            }
        }
    }
    
    /**
     * 블랙리스트 통계 조회
     */
    public BlacklistStats getBlacklistStats() {
        try {
            String totalCount = redisTemplate.opsForValue().get("blacklist:stats:total");
            Set<String> activeTokenKeys = redisTemplate.keys(BLACKLIST_KEY_PREFIX + "*");
            Set<String> activeUserKeys = redisTemplate.keys(USER_BLACKLIST_PREFIX + "*");
            
            return BlacklistStats.builder()
                    .totalBlacklistedCount(totalCount != null ? Long.valueOf(totalCount) : 0L)
                    .activeBlacklistedCount(activeTokenKeys != null ? activeTokenKeys.size() : 0)
                    .blacklistedUsersCount(activeUserKeys != null ? activeUserKeys.size() : 0)
                    .build();
                    
        } catch (Exception e) {
            log.error("블랙리스트 통계 조회 실패: {}", e.getMessage());
            return BlacklistStats.builder().build();
        }
    }
    
    /**
     * 특정 사용자의 블랙리스트된 토큰 개수 조회
     */
    public long getUserBlacklistCount(Long userId) {
        try {
            String userKey = USER_BLACKLIST_PREFIX + userId;
            Long count = redisTemplate.opsForSet().size(userKey);
            return count != null ? count : 0L;
            
        } catch (Exception e) {
            log.error("사용자 블랙리스트 토큰 개수 조회 실패 - userId: {}, error: {}", 
                    userId, e.getMessage());
            return 0L;
        }
    }
}
```

### BlacklistStats.java (DTO)
```java
package com.routepick.backend.security.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 토큰 블랙리스트 통계 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistStats {
    
    /**
     * 총 블랙리스트된 토큰 수 (누적)
     */
    private Long totalBlacklistedCount;
    
    /**
     * 현재 활성 블랙리스트 토큰 수
     */
    private Integer activeBlacklistedCount;
    
    /**
     * 블랙리스트된 사용자 수
     */
    private Integer blacklistedUsersCount;
    
    /**
     * 마지막 정리 시간
     */
    private String lastCleanupTime;
}
```

## 🔄 통합 설정

### SecurityConfig에 통합
```java
@Configuration
public class SecurityConfig {
    
    @Autowired
    private TokenBlacklistService tokenBlacklistService;
    
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(tokenBlacklistService);
    }
}
```

### JwtAuthenticationFilter에서 사용
```java
@Override
protected void doFilterInternal(HttpServletRequest request, 
                               HttpServletResponse response, 
                               FilterChain filterChain) throws ServletException, IOException {
    
    String token = extractToken(request);
    
    if (token != null) {
        // 토큰 블랙리스트 확인
        if (tokenBlacklistService.isTokenBlacklisted(token)) {
            log.warn("블랙리스트된 토큰으로 접근 시도 - IP: {}", getClientIP(request));
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        
        // JWT 검증 진행
        if (jwtTokenProvider.validateToken(token)) {
            Authentication auth = jwtTokenProvider.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }
    
    filterChain.doFilter(request, response);
}
```

## ⏰ 스케줄러 설정

### TokenCleanupScheduler.java
```java
package com.routepick.backend.security.scheduler;

import com.routepick.backend.security.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토큰 블랙리스트 정리 스케줄러
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TokenCleanupScheduler {
    
    private final TokenBlacklistService tokenBlacklistService;
    
    /**
     * 매 시간마다 만료된 토큰 정리
     */
    @Scheduled(cron = "0 0 * * * *") // 매 시간 정각
    public void cleanupExpiredTokens() {
        log.info("만료된 토큰 정리 시작");
        tokenBlacklistService.cleanupExpiredTokens();
    }
}
```

## 📊 성능 최적화

### Redis 설정 최적화
```yaml
# application.yml
spring:
  redis:
    jedis:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
    timeout: 2000ms
    
# 토큰 블랙리스트 설정
security:
  token:
    blacklist:
      cleanup-interval: 60 # 분
      max-entries: 100000
      redis-key-expiry: 7 # 일
```

## 🛡️ 보안 고려사항

### 1. 메모리 사용량 제어
- TTL 기반 자동 만료
- 주기적 정리로 메모리 최적화
- Redis 메모리 사용량 모니터링

### 2. 성능 최적화
- O(1) 블랙리스트 조회
- 배치 처리로 네트워크 호출 최소화
- Redis 연결 풀 최적화

### 3. 고가용성
- Redis Cluster 지원
- 실패 시 안전한 기본값 (블랙리스트로 처리)
- 분산 락으로 정리 작업 중복 방지

---

**다음 파일**: step8-5b_security_audit_service.md  
**연관 시스템**: JWT 인증 시스템의 토큰 생명주기 관리  
**성능 목표**: 블랙리스트 조회 1ms 이내, 메모리 사용량 100MB 이하

*생성일: 2025-09-02*  
*RoutePickr 8-5a: JWT 토큰 블랙리스트 관리 시스템 완성*