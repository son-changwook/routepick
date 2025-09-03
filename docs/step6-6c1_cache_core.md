# 🚀 Step 6-6c1: Cache Core Service

> Redis 기반 핵심 캐시 관리 서비스  
> 생성일: 2025-09-01  
> 분할 기준: 캐시 기본 기능

---

## 🎯 설계 목표

- **캐시 관리**: Redis 기반 통합 캐시 관리 시스템
- **TTL 최적화**: 데이터 특성별 차등 TTL 전략
- **캐시 무효화**: 스마트 캐시 무효화 및 갱신
- **메모리 최적화**: 캐시 크기 관리 및 자동 정리

---

## ✅ CacheService.java (핵심 기능)

```java
package com.routepick.service.system;

import com.routepick.dto.system.CacheKeyDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 캐시 관리 핵심 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    
    // ===== 캐시 기본 관리 =====
    
    /**
     * 캐시 데이터 저장 (TTL 설정)
     */
    public void put(String cacheName, String key, Object value, Duration ttl) {
        try {
            String cacheKey = buildCacheKey(cacheName, key);
            
            redisTemplate.opsForValue().set(cacheKey, value, ttl);
            
            log.debug("캐시 저장: {} -> {} (TTL: {}초)", 
                    cacheKey, value != null ? "데이터" : "null", ttl.getSeconds());
            
        } catch (Exception e) {
            log.error("캐시 저장 실패: {} -> {}", cacheName, key, e);
        }
    }
    
    /**
     * 캐시 데이터 조회
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String cacheName, String key, Class<T> type) {
        try {
            String cacheKey = buildCacheKey(cacheName, key);
            
            Object value = redisTemplate.opsForValue().get(cacheKey);
            
            if (value != null && type.isInstance(value)) {
                log.debug("캐시 조회 성공: {}", cacheKey);
                return Optional.of((T) value);
            }
            
            log.debug("캐시 조회 실패: {} (miss)", cacheKey);
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("캐시 조회 실패: {} -> {}", cacheName, key, e);
            return Optional.empty();
        }
    }
    
    /**
     * 캐시 데이터 삭제
     */
    public void evict(String cacheName, String key) {
        try {
            String cacheKey = buildCacheKey(cacheName, key);
            
            Boolean deleted = redisTemplate.delete(cacheKey);
            
            log.debug("캐시 삭제: {} -> {}", cacheKey, deleted ? "성공" : "실패");
            
        } catch (Exception e) {
            log.error("캐시 삭제 실패: {} -> {}", cacheName, key, e);
        }
    }
    
    /**
     * 패턴별 캐시 삭제
     */
    public void evictByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keys);
                
                log.info("패턴별 캐시 삭제: {} -> {}개", pattern, deletedCount);
            }
            
        } catch (Exception e) {
            log.error("패턴별 캐시 삭제 실패: {}", pattern, e);
        }
    }
    
    /**
     * 캐시 존재 여부 확인
     */
    public boolean exists(String cacheName, String key) {
        try {
            String cacheKey = buildCacheKey(cacheName, key);
            return Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey));
        } catch (Exception e) {
            log.error("캐시 존재 확인 실패: {} -> {}", cacheName, key, e);
            return false;
        }
    }
    
    /**
     * 다중 캐시 저장
     */
    public void putAll(String cacheName, Map<String, Object> entries, Duration ttl) {
        try {
            entries.forEach((key, value) -> put(cacheName, key, value, ttl));
            log.debug("다중 캐시 저장 완료: {} -> {}개", cacheName, entries.size());
        } catch (Exception e) {
            log.error("다중 캐시 저장 실패: {}", cacheName, e);
        }
    }
    
    // ===== 캐시별 무효화 =====
    
    /**
     * 사용자 관련 캐시 무효화
     */
    @CacheEvict(value = {"users", "userProfiles", "userPreferences"}, allEntries = true)
    public void evictUserCaches() {
        log.info("사용자 관련 캐시 무효화 완료");
    }
    
    /**
     * 루트 관련 캐시 무효화
     */
    @CacheEvict(value = {"routes", "routeRecommendations", "routeTags"}, allEntries = true)
    public void evictRouteCaches() {
        log.info("루트 관련 캐시 무효화 완료");
    }
    
    /**
     * 체육관 관련 캐시 무효화
     */
    @CacheEvict(value = {"gyms", "gymBranches", "walls"}, allEntries = true)
    public void evictGymCaches() {
        log.info("체육관 관련 캐시 무효화 완료");
    }
    
    /**
     * 커뮤니티 관련 캐시 무효화
     */
    @CacheEvict(value = {"posts", "comments", "interactions"}, allEntries = true)
    public void evictCommunityCaches() {
        log.info("커뮤니티 관련 캐시 무효화 완료");
    }
    
    /**
     * 결제 관련 캐시 무효화
     */
    @CacheEvict(value = {"payments", "paymentRecords"}, allEntries = true)
    public void evictPaymentCaches() {
        log.info("결제 관련 캐시 무효화 완료");
    }
    
    /**
     * 알림 관련 캐시 무효화
     */
    @CacheEvict(value = {"notifications", "notices", "banners"}, allEntries = true)
    public void evictNotificationCaches() {
        log.info("알림 관련 캐시 무효화 완료");
    }
    
    /**
     * 시스템 관련 캐시 무효화
     */
    @CacheEvict(value = {"apiLogs", "externalApi", "systemStats"}, allEntries = true)
    public void evictSystemCaches() {
        log.info("시스템 관련 캐시 무효화 완료");
    }
    
    /**
     * 전체 캐시 무효화
     */
    @CacheEvict(value = {"users", "userProfiles", "userPreferences", "routes", "routeRecommendations", 
                        "routeTags", "gyms", "gymBranches", "walls", "posts", "comments", "interactions",
                        "payments", "paymentRecords", "notifications", "notices", "banners",
                        "apiLogs", "externalApi", "systemStats"}, allEntries = true)
    public void evictAllCaches() {
        log.info("전체 캐시 무효화 완료");
    }
    
    // ===== TTL 관리 =====
    
    /**
     * TTL 연장
     */
    public void extendTtl(String cacheName, String key, Duration additionalTtl) {
        try {
            String cacheKey = buildCacheKey(cacheName, key);
            
            Long currentTtl = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
            
            if (currentTtl != null && currentTtl > 0) {
                Duration newTtl = Duration.ofSeconds(currentTtl).plus(additionalTtl);
                redisTemplate.expire(cacheKey, newTtl);
                
                log.debug("TTL 연장: {} -> {}초", cacheKey, newTtl.getSeconds());
            }
            
        } catch (Exception e) {
            log.error("TTL 연장 실패: {} -> {}", cacheName, key, e);
        }
    }
    
    /**
     * TTL 설정
     */
    public void setTtl(String cacheName, String key, Duration ttl) {
        try {
            String cacheKey = buildCacheKey(cacheName, key);
            
            Boolean success = redisTemplate.expire(cacheKey, ttl);
            
            if (Boolean.TRUE.equals(success)) {
                log.debug("TTL 설정: {} -> {}초", cacheKey, ttl.getSeconds());
            }
            
        } catch (Exception e) {
            log.error("TTL 설정 실패: {} -> {}", cacheName, key, e);
        }
    }
    
    /**
     * TTL 조회
     */
    public Duration getTtl(String cacheName, String key) {
        try {
            String cacheKey = buildCacheKey(cacheName, key);
            
            Long ttl = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);
            
            return ttl != null && ttl > 0 ? Duration.ofSeconds(ttl) : Duration.ZERO;
            
        } catch (Exception e) {
            log.error("TTL 조회 실패: {} -> {}", cacheName, key, e);
            return Duration.ZERO;
        }
    }
    
    /**
     * TTL 제거 (영구 저장)
     */
    public void persist(String cacheName, String key) {
        try {
            String cacheKey = buildCacheKey(cacheName, key);
            
            Boolean success = redisTemplate.persist(cacheKey);
            
            if (Boolean.TRUE.equals(success)) {
                log.debug("TTL 제거 (영구 저장): {}", cacheKey);
            }
            
        } catch (Exception e) {
            log.error("TTL 제거 실패: {} -> {}", cacheName, key, e);
        }
    }
    
    // ===== 캐시 키 관리 =====
    
    /**
     * 캐시 키 목록 조회
     */
    public List<CacheKeyDto> getCacheKeys(String pattern, int limit) {
        try {
            Set<String> keys = redisTemplate.keys(pattern != null ? pattern : "*");
            
            if (keys == null) {
                return Collections.emptyList();
            }
            
            return keys.stream()
                    .limit(limit)
                    .map(this::createCacheKeyDto)
                    .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("캐시 키 목록 조회 실패", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 캐시 키 개수 조회
     */
    public long countKeys(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern != null ? pattern : "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("캐시 키 개수 조회 실패", e);
            return 0;
        }
    }
    
    /**
     * 랜덤 캐시 키 조회
     */
    public String randomKey() {
        try {
            return redisTemplate.randomKey();
        } catch (Exception e) {
            log.error("랜덤 캐시 키 조회 실패", e);
            return null;
        }
    }
    
    // ===== 유틸리티 메서드 =====
    
    /**
     * 캐시 키 생성
     */
    private String buildCacheKey(String cacheName, String key) {
        return String.format("%s:%s", cacheName, key);
    }
    
    /**
     * CacheKeyDto 생성
     */
    private CacheKeyDto createCacheKeyDto(String key) {
        try {
            Duration ttl = Duration.ofSeconds(
                redisTemplate.getExpire(key, TimeUnit.SECONDS)
            );
            
            return CacheKeyDto.builder()
                    .key(key)
                    .ttl(ttl)
                    .hasValue(redisTemplate.hasKey(key))
                    .build();
                    
        } catch (Exception e) {
            log.error("CacheKeyDto 생성 실패: {}", key, e);
            return CacheKeyDto.builder()
                    .key(key)
                    .hasValue(false)
                    .build();
        }
    }
    
    /**
     * 네임스페이스별 캐시 삭제
     */
    public void evictByNamespace(String namespace) {
        try {
            String pattern = namespace + ":*";
            evictByPattern(pattern);
        } catch (Exception e) {
            log.error("네임스페이스별 캐시 삭제 실패: {}", namespace, e);
        }
    }
    
    /**
     * 캐시 크기 조회 (바이트)
     */
    public long getKeySize(String cacheName, String key) {
        try {
            String cacheKey = buildCacheKey(cacheName, key);
            Object value = redisTemplate.opsForValue().get(cacheKey);
            
            if (value != null) {
                // 객체를 직렬화하여 크기 계산 (근사치)
                return value.toString().getBytes().length;
            }
            
            return 0;
        } catch (Exception e) {
            log.error("캐시 크기 조회 실패: {} -> {}", cacheName, key, e);
            return 0;
        }
    }
}
```

---

## 📈 주요 특징

### 1. **캐시 기본 관리**
- 저장, 조회, 삭제, 패턴 삭제
- 다중 캐시 저장
- 캐시 존재 여부 확인

### 2. **도메인별 캐시 무효화**
- 사용자, 루트, 체육관별 캐시 관리
- 커뮤니티, 결제, 알림 캐시 관리
- 전체 캐시 일괄 무효화

### 3. **TTL 관리**
- TTL 연장 및 설정
- TTL 조회 및 제거
- 영구 저장 설정

### 4. **캐시 키 관리**
- 패턴별 키 조회
- 키 개수 통계
- 랜덤 키 조회

---

**📝 연관 파일**: 
- step6-6c2_cache_warming.md (캐시 워밍업)
- step6-6c3_cache_statistics.md (캐시 통계)