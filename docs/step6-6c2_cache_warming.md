# 🚀 Step 6-6c2: Cache Warming & Preloading

> 캐시 워밍업 및 프리로딩 시스템  
> 생성일: 2025-09-01  
> 분할 기준: 캐시 워밍업 로직

---

## 🎯 설계 목표

- **캐시 워밍업**: 자주 사용되는 데이터 사전 로딩
- **스마트 프리로딩**: 접근 패턴 기반 데이터 프리로딩
- **정기 갱신**: 캐시 데이터 자동 갱신
- **성능 최적화**: 콜드 스타트 방지

---

## ✅ CacheWarmingService.java

```java
package com.routepick.service.system;

import com.routepick.service.user.UserService;
import com.routepick.service.route.RouteService;
import com.routepick.service.gym.GymService;
import com.routepick.service.tag.RecommendationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 캐시 워밍업 및 프리로딩 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheWarmingService {
    
    private final CacheService cacheService;
    private final UserService userService;
    private final RouteService routeService;
    private final GymService gymService;
    private final RecommendationService recommendationService;
    
    // 접근 패턴 추적
    private final Map<String, AccessPattern> accessPatterns = new ConcurrentHashMap<>();
    
    // ===== 시스템 시작 시 워밍업 =====
    
    /**
     * 애플리케이션 시작 시 캐시 워밍업
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        log.info("애플리케이션 시작 - 캐시 워밍업 시작");
        
        try {
            warmupCaches().get(); // 완료 대기
            log.info("캐시 워밍업 완료");
        } catch (Exception e) {
            log.error("캐시 워밍업 실패", e);
        }
    }
    
    /**
     * 전체 캐시 워밍업
     */
    public CompletableFuture<Void> warmupCaches() {
        log.info("캐시 워밍업 시작");
        
        List<CompletableFuture<Void>> futures = Arrays.asList(
            warmupUserCaches(),
            warmupRouteCaches(),
            warmupGymCaches(),
            warmupRecommendationCaches(),
            warmupSystemCaches()
        );
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    // ===== 도메인별 워밍업 =====
    
    /**
     * 사용자 캐시 워밍업
     */
    @Async
    public CompletableFuture<Void> warmupUserCaches() {
        try {
            log.debug("사용자 캐시 워밍업 시작");
            
            // 활성 사용자 목록
            var activeUsers = userService.getActiveUsers(100);
            cacheService.put("users:active", "list", activeUsers, Duration.ofMinutes(10));
            
            // 인기 사용자 프로필
            var popularUsers = userService.getPopularUsers(50);
            popularUsers.forEach(user -> 
                cacheService.put("userProfiles", String.valueOf(user.getId()), 
                    user, Duration.ofMinutes(15))
            );
            
            // 사용자 통계
            var userStats = userService.getUserStatistics();
            cacheService.put("users:stats", "global", userStats, Duration.ofMinutes(30));
            
            log.debug("사용자 캐시 워밍업 완료: {}개 항목", 
                activeUsers.size() + popularUsers.size() + 1);
            
        } catch (Exception e) {
            log.error("사용자 캐시 워밍업 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 루트 캐시 워밍업
     */
    @Async
    public CompletableFuture<Void> warmupRouteCaches() {
        try {
            log.debug("루트 캐시 워밍업 시작");
            
            // 인기 루트 목록
            var popularRoutes = routeService.getPopularRoutes(100);
            cacheService.put("routes:popular", "list", popularRoutes, Duration.ofMinutes(15));
            
            // 신규 루트 목록
            var recentRoutes = routeService.getRecentRoutes(50);
            cacheService.put("routes:recent", "list", recentRoutes, Duration.ofMinutes(5));
            
            // 난이도별 루트 통계
            var difficultyStats = routeService.getRoutesByDifficultyStats();
            cacheService.put("routes:difficulty", "stats", difficultyStats, Duration.ofMinutes(30));
            
            // 인기 루트 상세 정보 캐싱
            popularRoutes.stream().limit(20).forEach(route ->
                cacheService.put("routes", String.valueOf(route.getId()), 
                    route, Duration.ofMinutes(20))
            );
            
            log.debug("루트 캐시 워밍업 완료: {}개 항목", 
                popularRoutes.size() + recentRoutes.size() + 23);
            
        } catch (Exception e) {
            log.error("루트 캐시 워밍업 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 체육관 캐시 워밍업
     */
    @Async
    public CompletableFuture<Void> warmupGymCaches() {
        try {
            log.debug("체육관 캐시 워밍업 시작");
            
            // 인기 체육관 목록
            var popularGyms = gymService.getPopularGyms(50);
            cacheService.put("gyms:popular", "list", popularGyms, Duration.ofMinutes(20));
            
            // 지역별 체육관 목록
            var regionGyms = gymService.getGymsByRegion();
            regionGyms.forEach((region, gyms) ->
                cacheService.put("gyms:region", region, gyms, Duration.ofHours(1))
            );
            
            // 체육관 상세 정보 캐싱
            popularGyms.forEach(gym ->
                cacheService.put("gyms", String.valueOf(gym.getId()), 
                    gym, Duration.ofMinutes(30))
            );
            
            log.debug("체육관 캐시 워밍업 완료: {}개 항목", 
                popularGyms.size() + regionGyms.size());
            
        } catch (Exception e) {
            log.error("체육관 캐시 워밍업 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 추천 캐시 워밍업
     */
    @Async
    public CompletableFuture<Void> warmupRecommendationCaches() {
        try {
            log.debug("추천 캐시 워밍업 시작");
            
            // 글로벌 추천 통계
            var globalStats = recommendationService.getGlobalRecommendationStats();
            cacheService.put("recommendations:stats", "global", globalStats, Duration.ofMinutes(30));
            
            // 태그 인기도
            var tagPopularity = recommendationService.getTagPopularity();
            cacheService.put("tags:popularity", "ranking", tagPopularity, Duration.ofMinutes(20));
            
            // 추천 알고리즘 파라미터
            var algoParams = recommendationService.getAlgorithmParameters();
            cacheService.put("recommendations:params", "current", algoParams, Duration.ofHours(1));
            
            log.debug("추천 캐시 워밍업 완료");
            
        } catch (Exception e) {
            log.error("추천 캐시 워밍업 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 시스템 캐시 워밍업
     */
    @Async
    public CompletableFuture<Void> warmupSystemCaches() {
        try {
            log.debug("시스템 캐시 워밍업 시작");
            
            // 시스템 설정
            Map<String, Object> systemConfig = Map.of(
                "maintenanceMode", false,
                "maxUploadSize", "10MB",
                "sessionTimeout", 3600
            );
            cacheService.put("system:config", "general", systemConfig, Duration.ofHours(1));
            
            // 지원 언어
            List<String> supportedLanguages = Arrays.asList("ko", "en");
            cacheService.put("system:languages", "list", supportedLanguages, Duration.ofHours(2));
            
            // API 버전
            Map<String, String> apiVersions = Map.of(
                "current", "v1",
                "deprecated", "v0",
                "beta", "v2"
            );
            cacheService.put("system:api", "versions", apiVersions, Duration.ofHours(1));
            
            log.debug("시스템 캐시 워밍업 완료");
            
        } catch (Exception e) {
            log.error("시스템 캐시 워밍업 실패", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== 스마트 프리로딩 =====
    
    /**
     * 접근 패턴 기반 스마트 프리로딩 (30분마다)
     */
    @Scheduled(fixedRate = 1800000)
    @Async
    public void smartPreloading() {
        log.debug("스마트 프리로딩 시작");
        
        try {
            // 자주 접근되는 패턴 분석
            List<Map.Entry<String, AccessPattern>> frequentPatterns = 
                accessPatterns.entrySet().stream()
                    .filter(e -> e.getValue().isFrequent())
                    .sorted((e1, e2) -> Integer.compare(
                        e2.getValue().getAccessCount(), 
                        e1.getValue().getAccessCount()))
                    .limit(50)
                    .toList();
            
            // 상위 패턴에 대해 프리로딩
            for (var entry : frequentPatterns) {
                preloadByPattern(entry.getKey(), entry.getValue());
            }
            
            log.debug("스마트 프리로딩 완료: {}개 패턴", frequentPatterns.size());
            
        } catch (Exception e) {
            log.error("스마트 프리로딩 실패", e);
        }
    }
    
    /**
     * 패턴별 데이터 프리로딩
     */
    private void preloadByPattern(String pattern, AccessPattern accessPattern) {
        try {
            String[] parts = pattern.split(":");
            if (parts.length < 2) return;
            
            String domain = parts[0];
            String key = parts[1];
            
            switch (domain) {
                case "users" -> preloadUserData(key);
                case "routes" -> preloadRouteData(key);
                case "gyms" -> preloadGymData(key);
                case "recommendations" -> preloadRecommendationData(key);
                default -> log.debug("알 수 없는 도메인: {}", domain);
            }
            
            // 접근 패턴 업데이트
            accessPattern.updateLastPreloaded();
            
        } catch (Exception e) {
            log.error("패턴별 프리로딩 실패: {}", pattern, e);
        }
    }
    
    private void preloadUserData(String key) {
        if ("profile".equals(key)) {
            // 자주 접근되는 사용자 프로필 프리로딩
            var frequentUsers = userService.getFrequentlyAccessedUsers(20);
            frequentUsers.forEach(user ->
                cacheService.put("userProfiles", String.valueOf(user.getId()), 
                    user, Duration.ofMinutes(15))
            );
        }
    }
    
    private void preloadRouteData(String key) {
        if ("detail".equals(key)) {
            // 자주 조회되는 루트 상세 정보 프리로딩
            var frequentRoutes = routeService.getFrequentlyViewedRoutes(20);
            frequentRoutes.forEach(route ->
                cacheService.put("routes", String.valueOf(route.getId()), 
                    route, Duration.ofMinutes(20))
            );
        }
    }
    
    private void preloadGymData(String key) {
        if ("nearby".equals(key)) {
            // 주요 지역별 체육관 프리로딩
            var majorRegions = Arrays.asList("서울", "경기", "부산");
            majorRegions.forEach(region -> {
                var gyms = gymService.getGymsByCity(region);
                cacheService.put("gyms:city", region, gyms, Duration.ofMinutes(30));
            });
        }
    }
    
    private void preloadRecommendationData(String key) {
        if ("popular".equals(key)) {
            // 인기 추천 데이터 프리로딩
            var popularRecommendations = recommendationService.getPopularRecommendations(50);
            cacheService.put("recommendations:popular", "list", 
                popularRecommendations, Duration.ofMinutes(15));
        }
    }
    
    // ===== 정기 캐시 갱신 =====
    
    /**
     * 핵심 캐시 정기 갱신 (10분마다)
     */
    @Scheduled(fixedRate = 600000)
    @Async
    public void refreshCoreCaches() {
        log.debug("핵심 캐시 갱신 시작");
        
        try {
            // 활성 사용자 목록 갱신
            var activeUsers = userService.getActiveUsers(100);
            cacheService.put("users:active", "list", activeUsers, Duration.ofMinutes(10));
            
            // 인기 루트 목록 갱신
            var popularRoutes = routeService.getPopularRoutes(100);
            cacheService.put("routes:popular", "list", popularRoutes, Duration.ofMinutes(15));
            
            // 실시간 통계 갱신
            var realtimeStats = Map.of(
                "activeUsers", userService.getActiveUserCount(),
                "todayRoutes", routeService.getTodayRouteCount(),
                "totalClimbs", routeService.getTotalClimbCount()
            );
            cacheService.put("stats:realtime", "dashboard", realtimeStats, Duration.ofMinutes(5));
            
            log.debug("핵심 캐시 갱신 완료");
            
        } catch (Exception e) {
            log.error("핵심 캐시 갱신 실패", e);
        }
    }
    
    /**
     * 만료 임박 캐시 갱신 (5분마다)
     */
    @Scheduled(fixedRate = 300000)
    @Async
    public void refreshExpiringCaches() {
        log.debug("만료 임박 캐시 갱신 시작");
        
        try {
            // TTL이 5분 미만인 캐시 찾기
            var expiringKeys = cacheService.getCacheKeys("*", 1000).stream()
                .filter(key -> key.getTtl() != null && 
                              key.getTtl().getSeconds() > 0 && 
                              key.getTtl().getSeconds() < 300)
                .toList();
            
            // 중요한 캐시만 갱신
            for (var key : expiringKeys) {
                if (shouldRefresh(key.getKey())) {
                    refreshCache(key.getKey());
                }
            }
            
            log.debug("만료 임박 캐시 갱신 완료: {}개", expiringKeys.size());
            
        } catch (Exception e) {
            log.error("만료 임박 캐시 갱신 실패", e);
        }
    }
    
    private boolean shouldRefresh(String key) {
        // 갱신이 필요한 중요 캐시 판단
        return key.startsWith("users:active") || 
               key.startsWith("routes:popular") ||
               key.startsWith("gyms:popular") ||
               key.startsWith("recommendations:stats");
    }
    
    private void refreshCache(String key) {
        try {
            // 캐시 키에 따른 데이터 갱신
            if (key.startsWith("users:active")) {
                var activeUsers = userService.getActiveUsers(100);
                cacheService.put("users:active", "list", activeUsers, Duration.ofMinutes(10));
            }
            // 다른 캐시 타입별 갱신 로직...
            
        } catch (Exception e) {
            log.error("캐시 갱신 실패: {}", key, e);
        }
    }
    
    // ===== 접근 패턴 추적 =====
    
    /**
     * 캐시 접근 기록
     */
    public void recordAccess(String pattern) {
        accessPatterns.computeIfAbsent(pattern, k -> new AccessPattern())
                     .recordAccess();
    }
    
    /**
     * 접근 패턴 클래스
     */
    private static class AccessPattern {
        private int accessCount = 0;
        private LocalDateTime lastAccessed = LocalDateTime.now();
        private LocalDateTime lastPreloaded;
        
        public void recordAccess() {
            this.accessCount++;
            this.lastAccessed = LocalDateTime.now();
        }
        
        public void updateLastPreloaded() {
            this.lastPreloaded = LocalDateTime.now();
        }
        
        public boolean isFrequent() {
            // 최근 30분 내 5회 이상 접근
            return accessCount >= 5 && 
                   lastAccessed.isAfter(LocalDateTime.now().minusMinutes(30));
        }
        
        public boolean needsPreload() {
            // 마지막 프리로딩 후 10분 경과
            return lastPreloaded == null || 
                   lastPreloaded.isBefore(LocalDateTime.now().minusMinutes(10));
        }
        
        public int getAccessCount() {
            return accessCount;
        }
    }
}
```

---

## 📈 주요 특징

### 1. **시스템 시작 워밍업**
- 애플리케이션 시작 시 자동 실행
- 도메인별 병렬 워밍업
- 핵심 데이터 사전 로딩

### 2. **스마트 프리로딩**
- 접근 패턴 분석 기반
- 자주 사용되는 데이터 자동 감지
- 효율적인 메모리 사용

### 3. **정기 캐시 갱신**
- 핵심 캐시 10분마다 갱신
- 만료 임박 캐시 자동 갱신
- 실시간 통계 업데이트

### 4. **접근 패턴 추적**
- 캐시 접근 빈도 모니터링
- 패턴 기반 최적화
- 동적 프리로딩 전략

---

**📝 연관 파일**: 
- step6-6c1_cache_core.md (캐시 핵심)
- step6-6c3_cache_statistics.md (캐시 통계)