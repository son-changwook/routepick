# 캐싱 성능 테스트

## 개요
RoutePickr 시스템의 캐싱 전략과 성능을 검증합니다. Redis 캐시 효율성, TTL 설정, 히트/미스 비율, 캐시 무효화 전략 등을 종합적으로 테스트합니다.

## 테스트 클래스 구조

```java
package com.routepick.performance.caching;

import com.routepick.common.service.CacheService;
import com.routepick.user.service.UserService;
import com.routepick.gym.service.GymService;
import com.routepick.route.service.RouteService;
import com.routepick.recommendation.service.RecommendationService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * 캐싱 성능 테스트
 * 
 * 테스트 범위:
 * - Redis 캐시 성능 측정
 * - TTL 기반 캐시 만료 전략
 * - 캐시 히트/미스 비율 분석
 * - 캐시 무효화 성능
 * - 동시성 환경에서 캐시 동작
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("performance-test")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CachingPerformanceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--maxmemory", "256mb", "--maxmemory-policy", "allkeys-lru");

    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private GymService gymService;
    
    @Autowired
    private RouteService routeService;
    
    @Autowired
    private RecommendationService recommendationService;
    
    private String testUserToken;
    private Long testUserId;
    
    @BeforeEach
    void setUp() {
        // Redis 캐시 초기화
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        
        // 테스트 사용자 준비
        testUserToken = createTestUserToken();
        testUserId = 1L;
    }
    
    @AfterEach
    void tearDown() {
        // 캐시 정리
        cacheService.clearAll();
    }
    
    @Nested
    @DisplayName("사용자 데이터 캐싱 성능")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class UserDataCachingTest {
        
        @Test
        @Order(1)
        @DisplayName("[성능] 사용자 프로필 캐시 - TTL 5분")
        void userProfile_CachePerformance() throws Exception {
            // given
            HttpHeaders headers = createAuthHeaders(testUserToken);
            
            // when - 캐시 미스 (첫 번째 요청)
            long startTime1 = System.nanoTime();
            ResponseEntity<Map> response1 = restTemplate.exchange(
                "/api/v1/users/profile", HttpMethod.GET, 
                new HttpEntity<>(headers), Map.class);
            long cacheMissTime = Duration.ofNanos(System.nanoTime() - startTime1).toMillis();
            
            // then
            assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response1.getBody()).isNotNull();
            
            // when - 캐시 히트 (두 번째 요청)
            long startTime2 = System.nanoTime();
            ResponseEntity<Map> response2 = restTemplate.exchange(
                "/api/v1/users/profile", HttpMethod.GET, 
                new HttpEntity<>(headers), Map.class);
            long cacheHitTime = Duration.ofNanos(System.nanoTime() - startTime2).toMillis();
            
            // then
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response2.getBody()).isEqualTo(response1.getBody());
            
            // 성능 검증
            assertThat(cacheHitTime).isLessThan(cacheMissTime);
            assertThat(cacheHitTime).isLessThan(50L); // 50ms 이하
            assertThat(cacheMissTime).isLessThan(500L); // 500ms 이하
            
            double speedImprovement = (double) cacheMissTime / cacheHitTime;
            System.out.printf("👤 사용자 프로필 캐시 성능 - 미스: %dms, 히트: %dms (%.1fx 향상)%n", 
                    cacheMissTime, cacheHitTime, speedImprovement);
            
            // 최소 2배 이상 향상되어야 함
            assertThat(speedImprovement).isGreaterThanOrEqualTo(2.0);
        }
        
        @Test
        @Order(2)
        @DisplayName("[성능] 사용자 통계 캐시 - TTL 1시간")
        void userStatistics_CachePerformance() throws Exception {
            // given
            HttpHeaders headers = createAuthHeaders(testUserToken);
            
            // when - 첫 번째 요청 (DB 쿼리)
            long startTime1 = System.nanoTime();
            ResponseEntity<Map> response1 = restTemplate.exchange(
                "/api/v1/users/statistics", HttpMethod.GET, 
                new HttpEntity<>(headers), Map.class);
            long dbQueryTime = Duration.ofNanos(System.nanoTime() - startTime1).toMillis();
            
            assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            // when - 두 번째 요청 (캐시에서 조회)
            long startTime2 = System.nanoTime();
            ResponseEntity<Map> response2 = restTemplate.exchange(
                "/api/v1/users/statistics", HttpMethod.GET, 
                new HttpEntity<>(headers), Map.class);
            long cacheQueryTime = Duration.ofNanos(System.nanoTime() - startTime2).toMillis();
            
            // then
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cacheQueryTime).isLessThan(dbQueryTime);
            assertThat(cacheQueryTime).isLessThan(30L); // 30ms 이하
            
            System.out.printf("📊 사용자 통계 캐시 성능 - DB: %dms, 캐시: %dms%n", 
                    dbQueryTime, cacheQueryTime);
        }
        
        @Test
        @Order(3)
        @DisplayName("[성능] 사용자 선호도 캐시 무효화")
        void userPreferences_CacheInvalidation() throws Exception {
            // given
            HttpHeaders headers = createAuthHeaders(testUserToken);
            
            // 초기 선호도 조회 (캐시 생성)
            ResponseEntity<Map> initialResponse = restTemplate.exchange(
                "/api/v1/users/preferences", HttpMethod.GET, 
                new HttpEntity<>(headers), Map.class);
            assertThat(initialResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            // when - 선호도 업데이트 (캐시 무효화 트리거)
            String updateJson = """
                {
                    "preferredDifficulties": ["V3", "V4", "V5"],
                    "preferredTags": ["CRIMPING", "OVERHANG", "DYNAMIC"],
                    "preferredGymTypes": ["BOULDERING", "SPORT"]
                }
                """;
            
            long updateStartTime = System.nanoTime();
            ResponseEntity<String> updateResponse = restTemplate.exchange(
                "/api/v1/users/preferences", HttpMethod.PUT, 
                new HttpEntity<>(updateJson, headers), String.class);
            long updateTime = Duration.ofNanos(System.nanoTime() - updateStartTime).toMillis();
            
            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            // then - 업데이트된 선호도 조회 (새로운 캐시 생성)
            long newCacheStartTime = System.nanoTime();
            ResponseEntity<Map> newResponse = restTemplate.exchange(
                "/api/v1/users/preferences", HttpMethod.GET, 
                new HttpEntity<>(headers), Map.class);
            long newCacheTime = Duration.ofNanos(System.nanoTime() - newCacheStartTime).toMillis();
            
            assertThat(newResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(newResponse.getBody()).isNotEqualTo(initialResponse.getBody());
            
            // 캐시 무효화 성능 검증
            assertThat(updateTime).isLessThan(200L); // 200ms 이하
            assertThat(newCacheTime).isLessThan(100L); // 100ms 이하
            
            System.out.printf("♻️ 선호도 캐시 무효화 성능 - 업데이트: %dms, 재캐싱: %dms%n", 
                    updateTime, newCacheTime);
        }
    }
    
    @Nested
    @DisplayName("암장 데이터 캐싱 성능")
    class GymDataCachingTest {
        
        @Test
        @DisplayName("[성능] 암장 목록 캐시 - TTL 10분")
        void gymList_CachePerformance() throws Exception {
            // given
            HttpHeaders headers = createAuthHeaders(testUserToken);
            
            // when - 첫 번째 요청 (DB 쿼리)
            long startTime1 = System.nanoTime();
            ResponseEntity<List> response1 = restTemplate.exchange(
                "/api/v1/gyms?page=0&size=20", HttpMethod.GET, 
                new HttpEntity<>(headers), List.class);
            long dbQueryTime = Duration.ofNanos(System.nanoTime() - startTime1).toMillis();
            
            assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> gyms = response1.getBody();
            assertThat(gyms).isNotEmpty();
            
            // when - 두 번째 요청 (캐시에서 조회)
            long startTime2 = System.nanoTime();
            ResponseEntity<List> response2 = restTemplate.exchange(
                "/api/v1/gyms?page=0&size=20", HttpMethod.GET, 
                new HttpEntity<>(headers), List.class);
            long cacheQueryTime = Duration.ofNanos(System.nanoTime() - startTime2).toMillis();
            
            // then
            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response2.getBody().size()).isEqualTo(gyms.size());
            
            // 성능 검증
            assertThat(cacheQueryTime).isLessThan(dbQueryTime);
            assertThat(cacheQueryTime).isLessThan(30L); // 30ms 이하
            
            double cacheEfficiency = (double) (dbQueryTime - cacheQueryTime) / dbQueryTime;
            System.out.printf("🏢 암장 목록 캐시 성능 - DB: %dms, 캐시: %dms (%.1f%% 향상)%n", 
                    dbQueryTime, cacheQueryTime, cacheEfficiency * 100);
        }
        
        @Test
        @DisplayName("[성능] 위치 기반 암장 검색 캐시")
        void locationBasedGymSearch_CachePerformance() throws Exception {
            // given - 서울 강남구 좌표
            double latitude = 37.5173;
            double longitude = 127.0473;
            double radius = 5.0;
            
            HttpHeaders headers = createAuthHeaders(testUserToken);
            String searchUrl = String.format(
                "/api/v1/gyms/search?lat=%.6f&lng=%.6f&radius=%.1f",
                latitude, longitude, radius);
            
            // when - 첫 번째 검색 (공간 쿼리 실행)
            long spatialQueryStartTime = System.nanoTime();
            ResponseEntity<List> spatialResponse = restTemplate.exchange(
                searchUrl, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            long spatialQueryTime = Duration.ofNanos(System.nanoTime() - spatialQueryStartTime).toMillis();
            
            assertThat(spatialResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> nearbyGyms = spatialResponse.getBody();
            assertThat(nearbyGyms).isNotEmpty();
            
            // when - 동일한 검색 (캐시에서 조회)
            long cacheQueryStartTime = System.nanoTime();
            ResponseEntity<List> cachedResponse = restTemplate.exchange(
                searchUrl, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            long cacheQueryTime = Duration.ofNanos(System.nanoTime() - cacheQueryStartTime).toMillis();
            
            // then
            assertThat(cachedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cachedResponse.getBody().size()).isEqualTo(nearbyGyms.size());
            
            // 공간 쿼리 캐시 성능 검증
            assertThat(cacheQueryTime).isLessThan(spatialQueryTime);
            assertThat(cacheQueryTime).isLessThan(50L); // 50ms 이하
            assertThat(spatialQueryTime).isLessThan(1000L); // 1초 이하
            
            System.out.printf("🗺️ 위치 기반 검색 캐시 성능 - 공간쿼리: %dms, 캐시: %dms%n", 
                    spatialQueryTime, cacheQueryTime);
        }
    }
    
    @Nested
    @DisplayName("루트 추천 캐싱 성능")
    class RecommendationCachingTest {
        
        @Test
        @DisplayName("[성능] 개인화 추천 캐시 - TTL 30분")
        void personalizedRecommendation_CachePerformance() throws Exception {
            // given
            HttpHeaders headers = createAuthHeaders(testUserToken);
            
            // when - 첫 번째 추천 요청 (알고리즘 실행)
            long algorithmStartTime = System.nanoTime();
            ResponseEntity<List> algorithmResponse = restTemplate.exchange(
                "/api/v1/recommendations/personal?limit=10", HttpMethod.GET, 
                new HttpEntity<>(headers), List.class);
            long algorithmTime = Duration.ofNanos(System.nanoTime() - algorithmStartTime).toMillis();
            
            assertThat(algorithmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<Map<String, Object>> recommendations = algorithmResponse.getBody();
            assertThat(recommendations).hasSizeGreaterThan(0);
            
            // when - 동일한 추천 요청 (캐시에서 조회)
            long cacheStartTime = System.nanoTime();
            ResponseEntity<List> cachedResponse = restTemplate.exchange(
                "/api/v1/recommendations/personal?limit=10", HttpMethod.GET, 
                new HttpEntity<>(headers), List.class);
            long cacheTime = Duration.ofNanos(System.nanoTime() - cacheStartTime).toMillis();
            
            // then
            assertThat(cachedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(cachedResponse.getBody().size()).isEqualTo(recommendations.size());
            
            // 추천 알고리즘 캐시 성능 검증 (가장 중요한 성능 영역)
            assertThat(cacheTime).isLessThan(algorithmTime);
            assertThat(cacheTime).isLessThan(100L); // 100ms 이하
            assertThat(algorithmTime).isLessThan(3000L); // 3초 이하
            
            double performanceGain = (double) algorithmTime / cacheTime;
            System.out.printf("🎯 개인화 추천 캐시 성능 - 알고리즘: %dms, 캐시: %dms (%.1fx 향상)%n", 
                    algorithmTime, cacheTime, performanceGain);
            
            // 추천 캐시는 최소 5배 이상 성능 향상이 있어야 함
            assertThat(performanceGain).isGreaterThanOrEqualTo(5.0);
        }
        
        @Test
        @DisplayName("[성능] 일일 추천 캐시")
        void dailyRecommendation_CachePerformance() throws Exception {
            // given
            HttpHeaders headers = createAuthHeaders(testUserToken);
            
            // when - 일일 추천 생성
            long generationStartTime = System.nanoTime();
            ResponseEntity<List> generationResponse = restTemplate.exchange(
                "/api/v1/recommendations/daily", HttpMethod.GET, 
                new HttpEntity<>(headers), List.class);
            long generationTime = Duration.ofNanos(System.nanoTime() - generationStartTime).toMillis();
            
            assertThat(generationResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            
            // when - 동일한 일일 추천 조회
            long retrievalStartTime = System.nanoTime();
            ResponseEntity<List> retrievalResponse = restTemplate.exchange(
                "/api/v1/recommendations/daily", HttpMethod.GET, 
                new HttpEntity<>(headers), List.class);
            long retrievalTime = Duration.ofNanos(System.nanoTime() - retrievalStartTime).toMillis();
            
            // then
            assertThat(retrievalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(retrievalTime).isLessThan(generationTime);
            assertThat(retrievalTime).isLessThan(50L); // 50ms 이하
            
            System.out.printf("📅 일일 추천 캐시 성능 - 생성: %dms, 조회: %dms%n", 
                    generationTime, retrievalTime);
        }
    }
    
    @Nested
    @DisplayName("동시성 캐시 성능")
    class ConcurrentCachingTest {
        
        @Test
        @DisplayName("[성능] 동시 캐시 접근 - 100명 사용자")
        void concurrent_CacheAccess_100Users() throws Exception {
            // given
            int userCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(20);
            List<CompletableFuture<Long>> futures = new ArrayList<>();
            
            // when - 100명이 동시에 같은 데이터 조회
            for (int i = 0; i < userCount; i++) {
                CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        HttpHeaders headers = createAuthHeaders(testUserToken);
                        long startTime = System.nanoTime();
                        
                        ResponseEntity<List> response = restTemplate.exchange(
                            "/api/v1/gyms?page=0&size=10", HttpMethod.GET, 
                            new HttpEntity<>(headers), List.class);
                        
                        long responseTime = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
                        
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        return responseTime;
                        
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 요청 완료 대기
            List<Long> responseTimes = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            // then
            double averageResponseTime = responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(0.0);
            
            long maxResponseTime = responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .max()
                    .orElse(0L);
            
            long minResponseTime = responseTimes.stream()
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(0L);
            
            System.out.printf("🚀 동시 캐시 접근 성능 (%d명) - 평균: %.2fms, 최소: %dms, 최대: %dms%n",
                    userCount, averageResponseTime, minResponseTime, maxResponseTime);
            
            // 성능 기준 검증
            assertThat(averageResponseTime).isLessThan(200.0); // 평균 200ms 이하
            assertThat(maxResponseTime).isLessThan(1000L); // 최대 1초 이하
            
            executor.shutdown();
        }
        
        @Test
        @DisplayName("[성능] 캐시 stampede 방지")
        void cache_StampedePrevention() throws Exception {
            // given - 캐시 만료 직전 상황 시뮬레이션
            String cacheKey = "test:stampede:gym-list";
            cacheService.evict(cacheKey); // 캐시 초기화
            
            int concurrentRequests = 50;
            ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
            List<CompletableFuture<Boolean>> futures = new ArrayList<>();
            
            // when - 동시에 동일한 데이터 요청 (캐시 미스 상황)
            for (int i = 0; i < concurrentRequests; i++) {
                CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        HttpHeaders headers = createAuthHeaders(testUserToken);
                        ResponseEntity<List> response = restTemplate.exchange(
                            "/api/v1/gyms?page=0&size=5", HttpMethod.GET, 
                            new HttpEntity<>(headers), List.class);
                        
                        return response.getStatusCode() == HttpStatus.OK;
                        
                    } catch (Exception e) {
                        return false;
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // 모든 요청 완료 대기
            List<Boolean> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            // then
            long successCount = results.stream()
                    .mapToLong(success -> success ? 1 : 0)
                    .sum();
            
            double successRate = (double) successCount / concurrentRequests;
            
            System.out.printf("🛡️ 캐시 stampede 방지 테스트 - 성공률: %.1f%% (%d/%d)%n",
                    successRate * 100, successCount, concurrentRequests);
            
            // 90% 이상 성공률 보장 (stampede 상황에서도 안정적)
            assertThat(successRate).isGreaterThanOrEqualTo(0.9);
            
            executor.shutdown();
        }
    }
    
    @Test
    @DisplayName("[종합] 전체 캐시 시스템 성능 벤치마크")
    void comprehensive_CacheSystemBenchmark() throws Exception {
        System.out.println("=== 캐시 시스템 성능 벤치마크 시작 ===");
        
        HttpHeaders headers = createAuthHeaders(testUserToken);
        
        // 1. 사용자 데이터 캐시 성능
        long userCacheMiss = measureCachePerformance(
            "/api/v1/users/profile", headers, true);
        long userCacheHit = measureCachePerformance(
            "/api/v1/users/profile", headers, false);
        
        // 2. 암장 데이터 캐시 성능  
        long gymCacheMiss = measureCachePerformance(
            "/api/v1/gyms?page=0&size=10", headers, true);
        long gymCacheHit = measureCachePerformance(
            "/api/v1/gyms?page=0&size=10", headers, false);
        
        // 3. 추천 데이터 캐시 성능
        long recCacheMiss = measureCachePerformance(
            "/api/v1/recommendations/personal?limit=5", headers, true);
        long recCacheHit = measureCachePerformance(
            "/api/v1/recommendations/personal?limit=5", headers, false);
        
        // 4. 캐시 메모리 사용량 확인
        String memoryInfo = cacheService.getCacheMemoryInfo();
        
        // 결과 출력
        System.out.println("=== 캐시 성능 벤치마크 결과 ===");
        System.out.printf("사용자 캐시: 미스 %dms, 히트 %dms (%.1fx 향상)%n", 
                userCacheMiss, userCacheHit, (double)userCacheMiss / userCacheHit);
        System.out.printf("암장 캐시: 미스 %dms, 히트 %dms (%.1fx 향상)%n", 
                gymCacheMiss, gymCacheHit, (double)gymCacheMiss / gymCacheHit);
        System.out.printf("추천 캐시: 미스 %dms, 히트 %dms (%.1fx 향상)%n", 
                recCacheMiss, recCacheHit, (double)recCacheMiss / recCacheHit);
        System.out.println("캐시 메모리 정보: " + memoryInfo);
        
        // 성능 기준 검증
        assertThat((double)userCacheMiss / userCacheHit).isGreaterThanOrEqualTo(2.0);
        assertThat((double)gymCacheMiss / gymCacheHit).isGreaterThanOrEqualTo(2.0);
        assertThat((double)recCacheMiss / recCacheHit).isGreaterThanOrEqualTo(3.0);
        
        // 캐시 히트 응답 시간 기준
        assertThat(userCacheHit).isLessThan(50L);
        assertThat(gymCacheHit).isLessThan(30L);
        assertThat(recCacheHit).isLessThan(100L);
        
        System.out.println("=== 벤치마크 완료: 모든 캐시 성능 기준 통과 ===");
    }
    
    // ================================================================================================
    // Helper Methods
    // ================================================================================================
    
    private long measureCachePerformance(String url, HttpHeaders headers, boolean clearCache) 
            throws Exception {
        if (clearCache) {
            redisTemplate.getConnectionFactory().getConnection().flushAll();
        }
        
        long startTime = System.nanoTime();
        ResponseEntity<?> response = restTemplate.exchange(
            url, HttpMethod.GET, new HttpEntity<>(headers), Object.class);
        long responseTime = Duration.ofNanos(System.nanoTime() - startTime).toMillis();
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return responseTime;
    }
    
    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }
    
    private String createTestUserToken() {
        // 테스트용 JWT 토큰 생성 로직
        return "test-jwt-token-for-caching-performance";
    }
}
```

## 캐시 모니터링 및 분석

### 캐시 히트율 분석
```bash
# Redis 캐시 통계 확인
redis-cli info stats

# 캐시 키 분석
redis-cli --scan --pattern "routepick:*" | head -20

# 메모리 사용량 분석
redis-cli memory usage "routepick:user:profile:1"
```

### 성능 기준 (SLA)
- **캐시 히트 응답시간**: 50ms 이하
- **캐시 히트율**: 80% 이상
- **캐시 무효화**: 200ms 이하
- **동시 접근**: 100명 동시, 평균 200ms 이하
- **메모리 효율성**: 256MB 제한 내에서 운영

### 주요 캐시 전략
- [x] 사용자 데이터: TTL 5분, LRU 정책
- [x] 암장 데이터: TTL 10분, 위치 기반 캐싱
- [x] 추천 데이터: TTL 30분, 개인화 캐싱
- [x] Stampede 방지: 분산 락 구현
- [x] 캐시 워밍업: 애플리케이션 시작 시 자동 실행