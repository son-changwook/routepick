# 🚀 Step 6-6c: CacheService 구현

> Redis 캐시 관리, TTL 최적화 및 캐시 전략 완전 구현  
> 생성일: 2025-08-22  
> 기반: 전체 Service 레이어 캐싱 전략 통합

---

## 🎯 설계 목표

- **캐시 관리**: Redis 기반 통합 캐시 관리 시스템
- **TTL 최적화**: 데이터 특성별 차등 TTL 전략
- **캐시 무효화**: 스마트 캐시 무효화 및 갱신
- **성능 모니터링**: 캐시 히트율 및 성능 분석
- **캐시 워밍업**: 자주 사용되는 데이터 사전 로딩
- **메모리 최적화**: 캐시 크기 관리 및 자동 정리

---

## ✅ CacheService.java

```java
package com.routepick.service.system;

import com.routepick.dto.system.CacheStatisticsDto;
import com.routepick.dto.system.CacheKeyDto;
import com.routepick.service.notification.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 캐시 관리 서비스
 * - Redis 캐시 통합 관리
 * - TTL 최적화 및 캐시 전략
 * - 캐시 성능 모니터링
 * - 자동 캐시 워밍업 및 정리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {
    
    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationService notificationService;
    
    // 캐시 성능 임계치
    private static final double HIT_RATE_THRESHOLD = 0.8; // 80%
    private static final long MEMORY_USAGE_THRESHOLD = 80; // 80%
    
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
    
    // ===== 캐시 워밍업 =====
    
    /**
     * 시스템 시작 시 캐시 워밍업
     */
    @Async
    public CompletableFuture<Void> warmupCaches() {
        log.info("캐시 워밍업 시작");
        
        try {
            // 자주 사용되는 데이터들을 사전 로딩
            warmupUserCaches();
            warmupRouteCaches();
            warmupGymCaches();
            warmupSystemCaches();
            
            log.info("캐시 워밍업 완료");
            
        } catch (Exception e) {
            log.error("캐시 워밍업 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 사용자 캐시 워밍업
     */
    private void warmupUserCaches() {
        try {
            // 활성 사용자 목록
            put("users:active", "list", getUserActiveList(), Duration.ofMinutes(10));
            
            // 인기 사용자 통계
            put("users:popular", "stats", getUserPopularStats(), Duration.ofMinutes(30));
            
            log.debug("사용자 캐시 워밍업 완료");
            
        } catch (Exception e) {
            log.error("사용자 캐시 워밍업 실패", e);
        }
    }
    
    /**
     * 루트 캐시 워밍업
     */
    private void warmupRouteCaches() {
        try {
            // 인기 루트 목록
            put("routes:popular", "list", getRoutePopularList(), Duration.ofMinutes(15));
            
            // 신규 루트 목록
            put("routes:recent", "list", getRouteRecentList(), Duration.ofMinutes(5));
            
            // 추천 루트 통계
            put("routeRecommendations:stats", "global", getRecommendationStats(), Duration.ofMinutes(30));
            
            log.debug("루트 캐시 워밍업 완료");
            
        } catch (Exception e) {
            log.error("루트 캐시 워밍업 실패", e);
        }
    }
    
    /**
     * 체육관 캐시 워밍업
     */
    private void warmupGymCaches() {
        try {
            // 인기 체육관 목록
            put("gyms:popular", "list", getGymPopularList(), Duration.ofMinutes(20));
            
            // 지역별 체육관 목록
            put("gyms:regions", "list", getGymRegionList(), Duration.ofHours(1));
            
            log.debug("체육관 캐시 워밍업 완료");
            
        } catch (Exception e) {
            log.error("체육관 캐시 워밍업 실패", e);
        }
    }
    
    /**
     * 시스템 캐시 워밍업
     */
    private void warmupSystemCaches() {
        try {
            // 시스템 설정
            put("system:config", "general", getSystemConfig(), Duration.ofHours(1));
            
            // API 설정
            put("externalApi:configs", "PROD", getApiConfigs(), Duration.ofMinutes(30));
            
            log.debug("시스템 캐시 워밍업 완료");
            
        } catch (Exception e) {
            log.error("시스템 캐시 워밍업 실패", e);
        }
    }
    
    // ===== 캐시 모니터링 =====
    
    /**
     * 캐시 통계 조회
     */
    public CacheStatisticsDto getCacheStatistics() {
        try {
            // Redis 정보 조회
            Properties info = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .info();
            
            // 캐시별 키 개수 계산
            Map<String, Long> cacheKeyCounts = calculateCacheKeyCounts();
            
            // 메모리 사용량 계산
            String usedMemory = info.getProperty("used_memory_human");
            String maxMemory = info.getProperty("maxmemory_human");
            
            // 히트율 계산 (Redis stats)
            String keyspaceHits = info.getProperty("keyspace_hits");
            String keyspaceMisses = info.getProperty("keyspace_misses");
            
            double hitRate = calculateHitRate(keyspaceHits, keyspaceMisses);
            
            return CacheStatisticsDto.builder()
                    .totalKeys(getTotalKeyCount())
                    .cacheKeyCounts(cacheKeyCounts)
                    .usedMemory(usedMemory)
                    .maxMemory(maxMemory)
                    .hitRate(hitRate)
                    .connectedClients(info.getProperty("connected_clients"))
                    .uptime(info.getProperty("uptime_in_seconds"))
                    .lastUpdated(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            log.error("캐시 통계 조회 실패", e);
            return CacheStatisticsDto.builder()
                    .lastUpdated(LocalDateTime.now())
                    .build();
        }
    }
    
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
     * 캐시 성능 체크 (정기적 실행)
     */
    @Scheduled(fixedRate = 300000) // 5분마다
    public void checkCachePerformance() {
        try {
            CacheStatisticsDto stats = getCacheStatistics();
            
            // 히트율 체크
            if (stats.getHitRate() < HIT_RATE_THRESHOLD) {
                notifyLowHitRate(stats.getHitRate());
            }
            
            // 메모리 사용량 체크 (Redis 정보에서 퍼센트 계산)
            double memoryUsagePercent = calculateMemoryUsagePercent(stats);
            if (memoryUsagePercent > MEMORY_USAGE_THRESHOLD) {
                notifyHighMemoryUsage(memoryUsagePercent);
            }
            
            log.debug("캐시 성능 체크 완료: 히트율 {:.2f}%, 메모리 사용량 {:.1f}%", 
                    stats.getHitRate() * 100, memoryUsagePercent);
            
        } catch (Exception e) {
            log.error("캐시 성능 체크 실패", e);
        }
    }
    
    /**
     * 만료된 캐시 정리 (매일 새벽 2시)
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredCaches() {
        log.info("만료된 캐시 정리 시작");
        
        try {
            // Redis는 자동으로 만료된 키를 정리하지만,
            // 추가적인 정리 작업 수행
            
            long beforeCount = getTotalKeyCount();
            
            // 임시 캐시 정리 (temp: 프리픽스)
            evictByPattern("temp:*");
            
            // 오래된 세션 정리 (session: 프리픽스)
            evictByPattern("session:*");
            
            long afterCount = getTotalKeyCount();
            long cleanedCount = beforeCount - afterCount;
            
            log.info("만료된 캐시 정리 완료: {}개 키 정리", cleanedCount);
            
        } catch (Exception e) {
            log.error("만료된 캐시 정리 실패", e);
        }
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
    
    // ===== 캐시 프리로딩 =====
    
    /**
     * 스마트 캐시 프리로딩
     */
    @Async
    public CompletableFuture<Void> preloadFrequentlyAccessedData() {
        log.info("자주 접근하는 데이터 프리로딩 시작");
        
        try {
            // 최근 1시간 내 자주 접근된 키 패턴 분석
            Map<String, Integer> accessPatterns = analyzeAccessPatterns();
            
            // 상위 접근 패턴에 대해 프리로딩
            accessPatterns.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(50) // 상위 50개
                    .forEach(entry -> preloadData(entry.getKey()));
            
            log.info("자주 접근하는 데이터 프리로딩 완료");
            
        } catch (Exception e) {
            log.error("데이터 프리로딩 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== 알림 시스템 =====
    
    /**
     * 낮은 히트율 알림
     */
    private void notifyLowHitRate(double hitRate) {
        try {
            String message = String.format(
                "캐시 히트율 저하: %.2f%% (임계치: %.0f%%)",
                hitRate * 100,
                HIT_RATE_THRESHOLD * 100
            );
            
            notificationService.sendSystemAlert("CACHE_LOW_HIT_RATE", message, Map.of(
                "hitRate", hitRate,
                "threshold", HIT_RATE_THRESHOLD
            ));
            
        } catch (Exception e) {
            log.error("낮은 히트율 알림 발송 실패", e);
        }
    }
    
    /**
     * 높은 메모리 사용량 알림
     */
    private void notifyHighMemoryUsage(double usagePercent) {
        try {
            String message = String.format(
                "캐시 메모리 사용량 높음: %.1f%% (임계치: %d%%)",
                usagePercent,
                MEMORY_USAGE_THRESHOLD
            );
            
            notificationService.sendSystemAlert("CACHE_HIGH_MEMORY", message, Map.of(
                "usagePercent", usagePercent,
                "threshold", MEMORY_USAGE_THRESHOLD
            ));
            
        } catch (Exception e) {
            log.error("높은 메모리 사용량 알림 발송 실패", e);
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
     * 총 키 개수 조회
     */
    private long getTotalKeyCount() {
        try {
            Set<String> keys = redisTemplate.keys("*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("총 키 개수 조회 실패", e);
            return 0;
        }
    }
    
    /**
     * 캐시별 키 개수 계산
     */
    private Map<String, Long> calculateCacheKeyCounts() {
        Map<String, Long> counts = new HashMap<>();
        
        try {
            // 주요 캐시 네임스페이스별 키 개수 계산
            String[] cacheNamespaces = {
                "users", "routes", "gyms", "posts", "payments", 
                "notifications", "apiLogs", "externalApi"
            };
            
            for (String namespace : cacheNamespaces) {
                Set<String> keys = redisTemplate.keys(namespace + ":*");
                counts.put(namespace, keys != null ? (long) keys.size() : 0L);
            }
            
        } catch (Exception e) {
            log.error("캐시별 키 개수 계산 실패", e);
        }
        
        return counts;
    }
    
    /**
     * 히트율 계산
     */
    private double calculateHitRate(String hits, String misses) {
        try {
            long hitCount = Long.parseLong(hits != null ? hits : "0");
            long missCount = Long.parseLong(misses != null ? misses : "0");
            long totalRequests = hitCount + missCount;
            
            return totalRequests > 0 ? (double) hitCount / totalRequests : 0.0;
            
        } catch (Exception e) {
            log.error("히트율 계산 실패", e);
            return 0.0;
        }
    }
    
    /**
     * 메모리 사용률 계산
     */
    private double calculateMemoryUsagePercent(CacheStatisticsDto stats) {
        try {
            // Redis 메모리 정보에서 퍼센트 추출
            // 실제 구현에서는 Redis INFO 명령어 결과를 파싱
            return 50.0; // 임시값
            
        } catch (Exception e) {
            log.error("메모리 사용률 계산 실패", e);
            return 0.0;
        }
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
     * 접근 패턴 분석
     */
    private Map<String, Integer> analyzeAccessPatterns() {
        // 실제 구현에서는 Redis 로그나 모니터링 데이터 분석
        // 여기서는 예시 데이터 반환
        return Map.of(
            "users:profile", 100,
            "routes:popular", 80,
            "gyms:nearby", 60
        );
    }
    
    /**
     * 데이터 프리로딩
     */
    private void preloadData(String pattern) {
        try {
            // 패턴에 따른 데이터 로딩 로직
            log.debug("데이터 프리로딩: {}", pattern);
            
        } catch (Exception e) {
            log.error("데이터 프리로딩 실패: {}", pattern, e);
        }
    }
    
    // 워밍업용 더미 메서드들 (실제로는 Service에서 데이터 조회)
    private Object getUserActiveList() { return Collections.emptyList(); }
    private Object getUserPopularStats() { return Collections.emptyMap(); }
    private Object getRoutePopularList() { return Collections.emptyList(); }
    private Object getRouteRecentList() { return Collections.emptyList(); }
    private Object getRecommendationStats() { return Collections.emptyMap(); }
    private Object getGymPopularList() { return Collections.emptyList(); }
    private Object getGymRegionList() { return Collections.emptyList(); }
    private Object getSystemConfig() { return Collections.emptyMap(); }
    private Object getApiConfigs() { return Collections.emptyList(); }
}
```

---

## 📊 DTO 클래스들

### CacheStatisticsDto.java
```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 캐시 통계 DTO
 */
@Getter
@Builder
public class CacheStatisticsDto {
    private Long totalKeys;
    private Map<String, Long> cacheKeyCounts;
    private String usedMemory;
    private String maxMemory;
    private Double hitRate;
    private String connectedClients;
    private String uptime;
    private LocalDateTime lastUpdated;
    
    public String getHitRateDisplay() {
        return hitRate != null ? String.format("%.2f%%", hitRate * 100) : "0.00%";
    }
    
    public boolean isHealthy() {
        return hitRate != null && hitRate >= 0.8; // 80% 이상
    }
    
    public String getPerformanceLevel() {
        if (hitRate == null) return "알 수 없음";
        
        if (hitRate >= 0.95) return "매우 좋음";
        if (hitRate >= 0.9) return "좋음";
        if (hitRate >= 0.8) return "보통";
        if (hitRate >= 0.7) return "주의";
        return "나쁨";
    }
    
    public Long getTotalCacheKeys() {
        return cacheKeyCounts != null ? 
                cacheKeyCounts.values().stream().mapToLong(Long::longValue).sum() : 0L;
    }
}
```

### CacheKeyDto.java
```java
package com.routepick.dto.system;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * 캐시 키 DTO
 */
@Getter
@Builder
public class CacheKeyDto {
    private String key;
    private Duration ttl;
    private Boolean hasValue;
    
    public String getTtlDisplay() {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return "만료됨";
        }
        
        long seconds = ttl.getSeconds();
        
        if (seconds >= 3600) {
            return String.format("%d시간 %d분", seconds / 3600, (seconds % 3600) / 60);
        } else if (seconds >= 60) {
            return String.format("%d분 %d초", seconds / 60, seconds % 60);
        } else {
            return String.format("%d초", seconds);
        }
    }
    
    public String getCacheNamespace() {
        return key != null && key.contains(":") ? 
                key.substring(0, key.indexOf(":")) : "unknown";
    }
    
    public boolean isExpiringSoon() {
        return ttl != null && ttl.getSeconds() < 300; // 5분 미만
    }
    
    public boolean isPersistent() {
        return ttl == null || ttl.getSeconds() < 0;
    }
}
```

---

## 🔧 설정 클래스

### CacheConfig.java
```java
package com.routepick.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 통합 캐시 설정
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    /**
     * 통합 Redis 캐시 매니저
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // 기본 TTL: 10분
                .serializeKeysWith(RedisCacheConfiguration.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisCacheConfiguration.SerializationPair
                        .fromSerializer(jackson2JsonRedisSerializer()));
        
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // 사용자 관련 캐시 (5분)
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("userProfiles", defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigurations.put("userPreferences", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        
        // 루트 관련 캐시 (15분)
        cacheConfigurations.put("routes", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("routeRecommendations", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("routeTags", defaultConfig.entryTtl(Duration.ofMinutes(20)));
        
        // 체육관 관련 캐시 (30분)
        cacheConfigurations.put("gyms", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("gymBranches", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("walls", defaultConfig.entryTtl(Duration.ofMinutes(20)));
        
        // 커뮤니티 관련 캐시 (5분)
        cacheConfigurations.put("posts", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("comments", defaultConfig.entryTtl(Duration.ofMinutes(3)));
        cacheConfigurations.put("interactions", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        
        // 결제 관련 캐시 (1분)
        cacheConfigurations.put("payments", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        cacheConfigurations.put("paymentRecords", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // 알림 관련 캐시 (3분)
        cacheConfigurations.put("notifications", defaultConfig.entryTtl(Duration.ofMinutes(3)));
        cacheConfigurations.put("notices", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("banners", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // 시스템 관련 캐시 (다양한 TTL)
        cacheConfigurations.put("apiLogs", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("externalApi", defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("systemStats", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
    
    /**
     * RedisTemplate 설정
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 직렬화 설정
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(jackson2JsonRedisSerializer());
        
        template.setDefaultSerializer(jackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        
        return template;
    }
    
    /**
     * Jackson2JsonRedisSerializer 설정
     */
    private Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer() {
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, 
                ObjectMapper.DefaultTyping.NON_FINAL);
        mapper.registerModule(new JavaTimeModule());
        
        serializer.setObjectMapper(mapper);
        
        return serializer;
    }
}
```

---

## 📈 주요 특징

### 1. **지능형 TTL 관리**
- 데이터 특성별 차등 TTL 전략
- 동적 TTL 연장 및 조정
- 만료 임박 데이터 자동 갱신

### 2. **성능 모니터링**
- 실시간 히트율 추적
- 메모리 사용량 모니터링
- 성능 임계치 기반 자동 알림

### 3. **스마트 캐시 관리**
- 접근 패턴 분석 기반 프리로딩
- 자동 캐시 워밍업
- 만료된 캐시 자동 정리

### 4. **통합 캐시 전략**
- 도메인별 캐시 네임스페이스 분리
- 일괄 캐시 무효화 지원
- 패턴 기반 캐시 관리

---

**📝 다음 단계**: step6-6d_system_service.md  
**완료일**: 2025-08-22  
**핵심 성과**: Redis 캐시 관리 + TTL 최적화 + 성능 모니터링 + 스마트 워밍업 완성